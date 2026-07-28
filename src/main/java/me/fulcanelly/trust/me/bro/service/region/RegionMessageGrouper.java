package me.fulcanelly.trust.me.bro.service.region;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups interaction rows for the Telegram warning text.
 *
 * Why this exists: the DB may store one row per owner-region, so the same place
 * shows up many times (one line per owner). For the message we merge nearby
 * boxes using {@code merge-distance} and list those owners under one header.
 *
 * This does not update SQLite — display only.
 */
public final class RegionMessageGrouper {

    private RegionMessageGrouper() {
    }

    /**
     * @param interactions  rows already chosen for this Telegram message
     * @param mergeDistance from config
     *                      {@code detection.split-by-regions.merge-distance}
     * @return groups in a stable-ish order: no-region rows first (as-is), then
     *         clusters
     */
    public static List<RegionOwnerGroup> uniteAndGroupByRegion(
            List<InteractionCount> interactions,
            int mergeDistance //
    ) {
        List<RegionOwnerGroup> groups = new ArrayList<>();
        List<InteractionCount> interactionsWithRegion = new ArrayList<>();

        // Split: no coordinates → keep as its own line, never unite with others.
        for (InteractionCount interaction : interactions) {
            if (interaction.hasRegion()) {
                interactionsWithRegion.add(interaction);
            } else {
                groups.add(new RegionOwnerGroup(null, List.of(interaction)));
            }
        }

        if (interactionsWithRegion.isEmpty()) {
            return groups;
        }

        // Grow clusters: each new row either starts a cluster or joins / merges nearby
        // ones.
        List<OpenCluster> openClusters = new ArrayList<>();

        for (InteractionCount interaction : interactionsWithRegion) {
            MessageRegion box = MessageRegion.fromInteraction(interaction);
            List<OpenCluster> nearbyClusters = findNearbyClusters(openClusters, box, mergeDistance);

            if (nearbyClusters.isEmpty()) {
                // Nothing close — new Telegram region block.
                openClusters.add(OpenCluster.startWith(interaction, box));
                continue;
            }

            // At least one nearby cluster: fold this row (and any other nearby clusters)
            // into the first.
            // Example: A near B, B near C → when C arrives it may touch both and glue A+B+C
            // together.
            OpenCluster target = nearbyClusters.get(0);
            target.add(interaction, box);

            for (int i = 1; i < nearbyClusters.size(); i++) {
                OpenCluster extra = nearbyClusters.get(i);
                target.mergeFrom(extra);
                openClusters.remove(extra);
            }
        }

        for (OpenCluster cluster : openClusters) {
            groups.add(cluster.toGroup());
        }
        return groups;
    }

    private static List<OpenCluster> findNearbyClusters(
            List<OpenCluster> openClusters,
            MessageRegion box,
            int mergeDistance //
    ) {
        List<OpenCluster> nearby = new ArrayList<>();
        for (OpenCluster cluster : openClusters) {
            if (cluster.bounds.isWithinMergeDistanceOf(box, mergeDistance)) {
                nearby.add(cluster);
            }
        }
        return nearby;
    }

    /**
     * Mutable cluster while we walk the list. Converted to RegionOwnerGroup at the
     * end.
     */
    private static final class OpenCluster {

        private MessageRegion bounds;
        private final List<InteractionCount> members = new ArrayList<>();

        private static OpenCluster startWith(InteractionCount interaction, MessageRegion box) {
            OpenCluster cluster = new OpenCluster();
            cluster.bounds = box;
            cluster.members.add(interaction);
            return cluster;
        }

        private void add(InteractionCount interaction, MessageRegion box) {
            members.add(interaction);
            bounds = bounds.expandToCover(box);
        }

        private void mergeFrom(OpenCluster other) {
            members.addAll(other.members);
            bounds = bounds.expandToCover(other.bounds);
        }

        private RegionOwnerGroup toGroup() {
            return new RegionOwnerGroup(bounds, List.copyOf(members));
        }
    }
}

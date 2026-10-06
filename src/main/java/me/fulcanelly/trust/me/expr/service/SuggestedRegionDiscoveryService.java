package me.fulcanelly.trust.me.expr.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.ActivityBox;
import me.fulcanelly.trust.me.expr.model.DiscoveryResult;
import me.fulcanelly.trust.me.expr.model.NamedRegionBox;
import me.fulcanelly.trust.me.expr.model.NewSuggestedRegion;
import me.fulcanelly.trust.me.expr.model.SuggestedRegion;
import me.fulcanelly.trust.me.expr.repository.ActivityBoxRepository;
import me.fulcanelly.trust.me.expr.util.Aabb;

/**
 * Path-B loop: page activity boxes → hard-skip named → absorb existing suggested →
 * merge/create new suggested. Greedy first-hit (order-dependent).
 */
@RequiredArgsConstructor
public final class SuggestedRegionDiscoveryService {

    private final ActivityBoxRepository activityBoxes;
    private final int pageSize;
    private final int mergeDistance;

    /** Pre-stat: activity boxes not near any named region (ignores suggested). */
    public int countUncoveredByNamed(List<NamedRegionBox> namedRegions) throws SQLException {
        int uncovered = 0;
        int offset = 0;
        while (true) {
            List<ActivityBox> page = activityBoxes.findPage(offset, pageSize);
            if (page.isEmpty()) {
                break;
            }
            for (ActivityBox activityBox : page) {
                if (!isCoveredByNamed(activityBox, namedRegions)) {
                    uncovered++;
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return uncovered;
    }

    public DiscoveryResult discover(
            List<NamedRegionBox> namedRegions,
            List<SuggestedRegion> existingSuggested //
    ) throws SQLException {
        List<NewSuggestedRegion> newSuggested = new ArrayList<>();
        int offset = 0;
        int scanned = 0;
        int skippedNamed = 0;

        while (true) {
            List<ActivityBox> page = activityBoxes.findPage(offset, pageSize);
            if (page.isEmpty()) {
                break;
            }
            scanned += page.size();

            for (ActivityBox activityBox : page) {
                if (isCoveredByNamed(activityBox, namedRegions)) {
                    skippedNamed++;
                    continue;
                }
                if (absorbIntoExisting(existingSuggested, activityBox)) {
                    continue;
                }
                mergeOrCreate(newSuggested, activityBox);
            }

            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        List<SuggestedRegion> dirtySuggested = new ArrayList<>();
        for (SuggestedRegion region : existingSuggested) {
            if (region.isDirty()) {
                dirtySuggested.add(region);
            }
        }

        newSuggested.sort(Comparator.comparingInt((NewSuggestedRegion r) -> r.getActivityBoxIds().size()).reversed());
        return new DiscoveryResult(scanned, skippedNamed, dirtySuggested, newSuggested);
    }

    private boolean absorbIntoExisting(List<SuggestedRegion> existing, ActivityBox activityBox) {
        for (SuggestedRegion region : existing) {
            if (region.absorb(activityBox, mergeDistance)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCoveredByNamed(ActivityBox activityBox, List<NamedRegionBox> namedRegions) {
        for (NamedRegionBox named : namedRegions) {
            if (Aabb.withinMergeDistance(
                    activityBox.getWid(), activityBox.getMinX(), activityBox.getMinZ(),
                    activityBox.getMaxX(), activityBox.getMaxZ(),
                    named.getWid(), named.getMinX(), named.getMinZ(), named.getMaxX(), named.getMaxZ(),
                    mergeDistance)) {
                return true;
            }
        }
        return false;
    }

    private void mergeOrCreate(List<NewSuggestedRegion> newSuggested, ActivityBox activityBox) {
        for (NewSuggestedRegion region : newSuggested) {
            if (Aabb.withinMergeDistance(
                    activityBox.getWid(), activityBox.getMinX(), activityBox.getMinZ(),
                    activityBox.getMaxX(), activityBox.getMaxZ(),
                    region.getWid(), region.getMinX(), region.getMinZ(), region.getMaxX(), region.getMaxZ(),
                    mergeDistance)) {
                region.merge(activityBox);
                return;
            }
        }
        newSuggested.add(NewSuggestedRegion.from(activityBox));
    }
}

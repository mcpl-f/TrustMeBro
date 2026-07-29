package me.fulcanelly.trust.me.expr.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.ActivityBox;
import me.fulcanelly.trust.me.expr.model.DiscoveryResult;
import me.fulcanelly.trust.me.expr.model.RegionBox;
import me.fulcanelly.trust.me.expr.model.SuggestedCandidate;
import me.fulcanelly.trust.me.expr.repository.InteractionCountsExprRepository;
import me.fulcanelly.trust.me.expr.util.Aabb;

/**
 * Page activity → skip covered → online-merge nearby boxes into candidates.
 */
@RequiredArgsConstructor
public final class SuggestedRegionDiscoveryService {

    private final InteractionCountsExprRepository interactionCounts;
    private final int pageSize;
    private final int mergeDistance;

    /**
     * How many geo activity boxes are outside the given coverage
     * (typically named regions only — suggested ignored).
     */
    public int countNotCovered(List<RegionBox> coverage) throws SQLException {
        int uncovered = 0;
        int offset = 0;
        while (true) {
            List<ActivityBox> page = interactionCounts.findPageWithGeometry(offset, pageSize);
            if (page.isEmpty()) {
                break;
            }
            for (ActivityBox box : page) {
                if (!isCovered(box, coverage)) {
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

    public DiscoveryResult discover(List<RegionBox> coverage) throws SQLException {
        List<SuggestedCandidate> candidates = new ArrayList<>();
        int offset = 0;
        int scanned = 0;
        int skippedCovered = 0;

        while (true) {
            List<ActivityBox> page = interactionCounts.findPageWithGeometry(offset, pageSize);
            if (page.isEmpty()) {
                break;
            }
            scanned += page.size();

            for (ActivityBox box : page) {
                if (isCovered(box, coverage)) {
                    skippedCovered++;
                    continue;
                }
                mergeOrCreate(candidates, box);
            }

            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        candidates.sort(Comparator.comparingLong(SuggestedCandidate::getWeight).reversed());
        return new DiscoveryResult(scanned, skippedCovered, candidates);
    }

    private boolean isCovered(ActivityBox box, List<RegionBox> coverage) {
        RegionBox activity = new RegionBox(
                "activity", box.getWid(), box.getMinX(), box.getMinZ(), box.getMaxX(), box.getMaxZ());
        for (RegionBox region : coverage) {
            if (Aabb.withinMergeDistance(activity, region, mergeDistance)) {
                return true;
            }
        }
        return false;
    }

    private void mergeOrCreate(List<SuggestedCandidate> candidates, ActivityBox box) {
        for (SuggestedCandidate candidate : candidates) {
            if (Aabb.withinMergeDistance(
                    box.getWid(), box.getMinX(), box.getMinZ(), box.getMaxX(), box.getMaxZ(),
                    candidate.getWid(), candidate.getMinX(), candidate.getMinZ(), candidate.getMaxX(),
                    candidate.getMaxZ(),
                    mergeDistance)) {
                candidate.merge(box);
                return;
            }
        }
        candidates.add(SuggestedCandidate.from(box));
    }
}

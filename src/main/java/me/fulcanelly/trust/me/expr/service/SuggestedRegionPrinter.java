package me.fulcanelly.trust.me.expr.service;

import java.util.List;
import java.util.Locale;

import me.fulcanelly.trust.me.expr.model.NewSuggestedRegion;

/** Console dump of new suggested regions. */
public final class SuggestedRegionPrinter {

    public void printTop(List<NewSuggestedRegion> newSuggested, int topN) {
        System.out.println("=== Top " + topN + " new suggested regions (this run) ===");
        System.out.println();

        if (newSuggested.isEmpty()) {
            System.out.println("(none — everything uncovered is already suggested or named)");
            return;
        }

        int show = Math.min(topN, newSuggested.size());
        for (int i = 0; i < show; i++) {
            printOne(i + 1, newSuggested.get(i));
        }
    }

    private void printOne(int rank, NewSuggestedRegion region) {
        System.out.printf(Locale.ROOT, "region #%d (activity boxes %d)%n", rank, region.getActivityBoxIds().size());
        System.out.printf(
                Locale.ROOT,
                "- wid=%d  min=(%d,%d)  max=(%d,%d)%n",
                region.getWid(),
                region.getMinX(),
                region.getMinZ(),
                region.getMaxX(),
                region.getMaxZ());
        System.out.println();
    }
}

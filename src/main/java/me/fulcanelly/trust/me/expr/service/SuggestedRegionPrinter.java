package me.fulcanelly.trust.me.expr.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.expr.model.SuggestedCandidate;

@RequiredArgsConstructor
public final class SuggestedRegionPrinter {

    private final int nameLimit;

    public void printTop(List<SuggestedCandidate> candidates, int topN) {
        System.out.println("=== Top " + topN + " new suggested regions (this run) ===");
        System.out.println();

        if (candidates.isEmpty()) {
            System.out.println("(none — everything uncovered is already suggested or named)");
            return;
        }

        int show = Math.min(topN, candidates.size());
        for (int i = 0; i < show; i++) {
            printOne(i + 1, candidates.get(i));
        }
    }

    private void printOne(int rank, SuggestedCandidate c) {
        System.out.printf(Locale.ROOT, "region #%d (weight %d)%n", rank, c.getWeight());
        System.out.println("- interactors: " + joinLimited(c.getInteractors()));
        System.out.println("- owners: " + joinLimited(c.getOwners()));
        System.out.printf(
                Locale.ROOT,
                "- world wid=%d  x=%d, z=%d%n",
                c.getWid(),
                c.centerX(),
                c.centerZ());
        System.out.printf(Locale.ROOT, "- ± %d%n", c.displayRadius());
        System.out.println();
    }

    private String joinLimited(Set<String> names) {
        if (names.isEmpty()) {
            return "(none)";
        }
        List<String> list = new ArrayList<>(names);
        int n = Math.min(nameLimit, list.size());
        String joined = String.join(", ", list.subList(0, n));
        int others = list.size() - n;
        if (others > 0) {
            return joined + " … +" + others;
        }
        return joined;
    }
}

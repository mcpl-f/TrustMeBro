package me.fulcanelly.trust.me.expr.model;

import java.util.List;

import lombok.Value;

/** Result of one discovery pass. */
@Value
public class DiscoveryResult {

    int scanned;
    int skippedCovered;
    List<SuggestedCandidate> newCandidates;
}

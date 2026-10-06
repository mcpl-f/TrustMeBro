package me.fulcanelly.trust.me.expr.model;

import java.util.List;

import lombok.Value;

/** One discover pass: counters + dirty persisted + brand-new suggested. */
@Value
public class DiscoveryResult {

    int scanned;
    int skippedNamed;
    List<SuggestedRegion> dirtySuggested;
    List<NewSuggestedRegion> newSuggested;
}

package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

import java.util.List;

/**
 * Named regions an interactor touched, with a capped name list for display.
 */
@Value
public class NamedRegionHits {

    /** Top region names (already limited). */
    List<String> names;

    /** Total distinct matching named regions (may be &gt; names.size()). */
    int totalCount;
}

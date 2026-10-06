/**
 * Offline Path-B experiment: discover suggested regions from activity boxes.
 *
 * <p>Vocab: {@code ActivityBox} (SQL {@code interaction_counts}),
 * {@code NamedRegionBox} ({@code regions}), {@code SuggestedRegion}.
 * Flow: page → skip named → absorb suggested → merge new → persist.
 *
 * <p>Details / simplify plan: {@code expr.md}.
 */
package me.fulcanelly.trust.me.expr;

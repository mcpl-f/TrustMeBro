package me.fulcanelly.trust.me.expr.model;

import lombok.Value;

/** One activity row from {@code interaction_counts} with geometry. */
@Value
public class ActivityBox {

    long id;
    String interactor;
    String owner;

    int wid;
    int minX;
    int minZ;

    int maxX;
    int maxZ;

    long weight; // (count_break_blocks + count_placed_blocks + count_interact_containers)
}

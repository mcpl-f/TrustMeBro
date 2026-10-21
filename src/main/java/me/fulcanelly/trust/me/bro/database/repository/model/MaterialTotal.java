package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

/**
 * Broken/placed counts of one material, summed over several interaction rows.
 *
 * <p>{@code material} is the namespaced key ({@code minecraft:stone}), the same format
 * as CoreProtect's {@code co_material_map.material}.
 *
 * <p>Owner-attributed: one physical block that touched two owners is counted twice.
 */
@Value
public class MaterialTotal {

    String material;
    int breakCount;
    int placeCount;

    public int total() {
        return breakCount + placeCount;
    }
}

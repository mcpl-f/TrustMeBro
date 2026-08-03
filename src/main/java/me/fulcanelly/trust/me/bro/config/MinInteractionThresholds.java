package me.fulcanelly.trust.me.bro.config;

import lombok.Value;

import org.bukkit.configuration.Configuration;

/**
 * {@code telegram.min-interactions}: when enabled, a pending row is eligible for
 * Telegram notify only if place, break, or chest count meets its minimum (OR).
 */
@Value
public class MinInteractionThresholds {

    boolean enabled;
    int placeCount;
    int breakCount;
    int chestInterCount;

    public static MinInteractionThresholds from(Configuration config) {
        return new MinInteractionThresholds(
                config.getBoolean("telegram.min-interactions.enabled", true),
                Math.max(1, config.getInt("telegram.min-interactions.place-count", 20)),
                Math.max(1, config.getInt("telegram.min-interactions.break-count", 10)),
                Math.max(1, config.getInt("telegram.min-interactions.chest-inter-count", 1)));
    }
}

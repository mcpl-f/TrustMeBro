package me.fulcanelly.trust.me.bro.service.text;

import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

public final class MinecraftWarningMessageBuilder {

    public String build(String ownerPlayer, List<InteractionCount> counts) {
        if (counts.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("Suspicious interactions involving your blocks:\n");
        for (InteractionCount count : counts) {
            builder.append("- ").append(count.getInteractorPlayer()).append(": ");
            appendPart(builder, count.getCountBreakBlocks(), "removed");
            appendPart(builder, count.getCountPlacedBlocks(), "placed");
            appendPart(builder, count.getCountInteractContainers(), "container interactions");
            builder.append('\n');
        }
        builder.append("Check Telegram for trust/report actions.");
        return builder.toString();
    }

    private void appendPart(StringBuilder builder, int value, String label) {
        if (value <= 0) {
            return;
        }
        if (builder.charAt(builder.length() - 1) != ' ') {
            builder.append(", ");
        }
        builder.append(value).append(' ').append(label);
    }
}

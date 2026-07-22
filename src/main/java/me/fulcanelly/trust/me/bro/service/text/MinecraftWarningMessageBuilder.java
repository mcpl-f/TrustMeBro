package me.fulcanelly.trust.me.bro.service.text;

import java.util.List;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MinecraftWarningMessageBuilder {

    private final LocalizationService messages;

    public String build(String ownerPlayer, List<InteractionCount> counts) {
        if (counts.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(messages.format("minecraft.warning.header")).append('\n');
        for (InteractionCount count : counts) {
            builder.append("- ").append(count.getInteractorPlayer()).append(": ");
            appendPart(builder, count.getCountBreakBlocks(), messages.format("count.removed"));
            appendPart(builder, count.getCountPlacedBlocks(), messages.format("count.placed"));
            appendPart(builder, count.getCountInteractContainers(), messages.format("count.container-interactions"));
            builder.append('\n');
        }
        builder.append(messages.format("minecraft.warning.footer"));
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

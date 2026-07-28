package me.fulcanelly.trust.me.bro.service.text;

import lombok.RequiredArgsConstructor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.tgbridge.utils.UsefulStuff;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.service.LocalizationService;
import me.fulcanelly.trust.me.bro.service.TrustCallbackPayloadService;
import me.fulcanelly.trust.me.bro.service.region.MessageRegion;
import me.fulcanelly.trust.me.bro.service.region.RegionMessageGrouper;
import me.fulcanelly.trust.me.bro.service.region.RegionOwnerGroup;

/**
 * Builds the Telegram warning message and its two action buttons.
 *
 * Telegram is the decision surface: owners can trust or report the interactor.
 * Callback payload contains only the action and interactor; owner is resolved
 * from
 * the Telegram account that clicks the button.
 *
 * Example:
 *
 * Player Griefer interacted with blocks associated with:
 *
 * - Owner: 3 removed, 1 container interactions
 *
 * buttons "Trust" and "Report" with callback data for Griefer.
 */
@RequiredArgsConstructor
public final class TelegramWarningMessageBuilder {

    private final SignupLoginReception reception;
    private final TrustCallbackPayloadService callbackPayloads;
    private final LocalizationService messages;
    private final CoreProtectReadRepository coreProtect;

    public String build(
            String interactorPlayer,
            List<InteractionCount> counts,
            int totalInteractions,
            int mergeDistance //
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(messages.format("telegram.warning.header", "interactor", escape(interactorPlayer))).append('\n');

        // Nearby owner-regions become one header + several "- owner: counts" lines.
        List<RegionOwnerGroup> groups = RegionMessageGrouper.uniteAndGroupByRegion(counts, mergeDistance);

        for (RegionOwnerGroup group : groups) {
            if (group.hasRegion()) {
                // Shared place first, then every owner touched there.
                builder.append(formatRegion(group.getRegion())).append('\n');
                for (InteractionCount count : group.getInteractions()) {
                    builder.append("- ").append(formatOwner(count.getOwnerPlayer())).append(": ");
                    builder.append(formatCounts(count)).append('\n');
                }
                builder.append('\n');
            } else {
                // Legacy / no coordinates: one owner line, no region header, no merging.
                for (InteractionCount count : group.getInteractions()) {
                    builder.append("- ").append(formatOwner(count.getOwnerPlayer())).append(": ");
                    builder.append(formatCounts(count)).append("\n\n");
                }
            }
        }

        if (totalInteractions > counts.size()) {
            builder.append(" ... ").append(totalInteractions - counts.size()).append(" more\n");
        }

        builder.append('\n').append(messages.format("telegram.warning.question")).append('\n');
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public String buildKeyboard(String interactorPlayer) {
        JSONObject keyboard = new JSONObject();
        JSONArray rows = new JSONArray();
        JSONArray row = new JSONArray();

        row.add(button(messages.format("telegram.button.trust"),
                callbackPayloads.encode(TrustCallbackPayloadService.ACTION_TRUST, interactorPlayer)));
        row.add(button(messages.format("telegram.button.report"),
                callbackPayloads.encode(TrustCallbackPayloadService.ACTION_REPORT, interactorPlayer)));
        rows.add(row);

        keyboard.put("inline_keyboard", rows);
        return keyboard.toJSONString();
    }

    @SuppressWarnings("unchecked")
    private JSONObject button(String text, String callbackData) {
        JSONObject button = new JSONObject();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }

    private String formatOwner(String player) {
        Optional<Long> telegramId = reception.getTgByUser(player);
        return telegramId.map(id -> UsefulStuff.telegramUserLink(player, id))
                .orElseGet(() -> escape(player));
    }

    private String formatCounts(InteractionCount count) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, count.getCountBreakBlocks(), messages.format("count.removed"));
        appendPart(builder, count.getCountPlacedBlocks(), messages.format("count.placed"));
        appendPart(builder, count.getCountInteractContainers(), messages.format("count.container-interactions"));
        return builder.length() == 0 ? "0" : builder.toString();
    }

    private String formatRegion(MessageRegion region) {
        return messages.format(
                "region.at",
                "world", formatWorld(region.getWorldId()),
                "x", region.centerX(),
                "z", region.centerZ(),
                "radius", region.displayRadius());
    }

    private String formatWorld(int wid) {
        String worldName = null;
        try {
            worldName = coreProtect.findWorldName(wid);
        } catch (SQLException ignored) {
            // fall through to id / name heuristics
        }
        // No Bukkit getWorld() here — this builder runs on the async notification job.
        return labelByWorldName(worldName, wid);
    }

    private String labelByWorldName(String worldName, int wid) {
        if (worldName == null || worldName.isBlank()) {
            return messages.format("world.unknown", "id", wid);
        }

        String key = worldName.toLowerCase(Locale.ROOT);
        if (key.endsWith("_nether") || key.equals("nether")) {
            return messages.format("world.nether");
        }
        if (key.endsWith("_the_end") || key.equals("the_end") || key.equals("end")) {
            return messages.format("world.end");
        }
        if (key.equals("world") || key.equals("overworld")) {
            return messages.format("world.overworld");
        }
        return messages.format("world.custom", "name", escape(worldName));
    }

    private void appendPart(StringBuilder builder, int value, String label) {
        if (value <= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value).append(' ').append(label);
    }

    private String escape(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
    }
}

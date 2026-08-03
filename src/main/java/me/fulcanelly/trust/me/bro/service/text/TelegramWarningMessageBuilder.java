package me.fulcanelly.trust.me.bro.service.text;

import lombok.RequiredArgsConstructor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.tgbridge.utils.UsefulStuff;
import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.RegionsRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegion;
import me.fulcanelly.trust.me.bro.service.region.MessageRegion;
import me.fulcanelly.trust.me.bro.service.region.RegionMessageGrouper;
import me.fulcanelly.trust.me.bro.service.region.RegionOwnerGroup;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;

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
    private final RegionsRepository regions;

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

        // Named admin regions (optional label next to coords when within
        // merge-distance).
        List<NamedRegion> namedRegions = loadNamedRegions();

        for (RegionOwnerGroup group : groups) {
            if (group.hasRegion()) {
                // Shared place first, then every owner touched there.
                builder.append(formatRegion(group.getRegion(), namedRegions, mergeDistance)).append('\n');
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

    private List<NamedRegion> loadNamedRegions() {
        try {
            return regions.findAll();
        } catch (SQLException e) {
            return List.of();
        }
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

    private String formatRegion(
            MessageRegion region,
            List<NamedRegion> namedRegions,
            int mergeDistance //
    ) {
        String world = formatWorld(region.getWorldId());
        String matchingNames = matchingNamedRegionLabels(region, namedRegions, mergeDistance);

        if (matchingNames.isEmpty()) {
            return messages.format(
                    "region.at",
                    "world", world,
                    "x", region.centerX(),
                    "z", region.centerZ(),
                    "radius", region.displayRadius());
        } else {
            return messages.format(
                    "region.at-named",
                    "name", escape(matchingNames),
                    "world", world,
                    "x", region.centerX(),
                    "z", region.centerZ(),
                    "radius", region.displayRadius());
        }
    }

    /**
     * Names of admin regions whose area is within mergeDistance of this interaction
     * box.
     */
    private String matchingNamedRegionLabels(
            MessageRegion interactionArea,
            List<NamedRegion> namedRegions,
            int mergeDistance //
    ) {
        return namedRegions.stream()
                .filter(named -> interactionArea.isWithinMergeDistanceOf(
                        named.toMessageRegion(), mergeDistance))
                .map(NamedRegion::getName)
                .collect(Collectors.joining(" / "));
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

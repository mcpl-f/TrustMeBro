package me.fulcanelly.trust.me.bro.service.text;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.regions.RegionsRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegion;
import me.fulcanelly.trust.me.bro.service.activitybox.MessageBox;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

/**
 * Builds the in-game warning with clickable Trust / Report actions for each interaction row.
 */
@RequiredArgsConstructor
public final class MinecraftWarningMessageBuilder {

    private final LocalizationService messages;
    private final CoreProtectReadRepository coreProtect;
    private final RegionsRepository regions;

    public BaseComponent[] build(List<InteractionCount> counts, int remainingInteractions, int mergeDistance) {
        List<BaseComponent> parts = new ArrayList<>();
        List<NamedRegion> namedRegions = loadNamedRegions();

        for (int i = 0; i < counts.size(); i++) {
            InteractionCount count = counts.get(i);
            String interactorPlayer = count.getInteractorPlayer();

            appendLine(
                    parts,
                    ChatColor.YELLOW,
                    messages.format("minecraft.warning.header", "interactor", interactorPlayer));
            parts.add(newline());

            if (count.hasActivityBox()) {
                appendPlainLines(
                        parts,
                        ChatColor.WHITE,
                        formatBox(MessageBox.fromInteraction(count), namedRegions, mergeDistance));
            }

            appendCountLine(parts, count.getCountBreakBlocks(), messages.format("count.removed"));
            appendCountLine(parts, count.getCountPlacedBlocks(), messages.format("count.placed"));
            appendCountLine(
                    parts,
                    count.getCountInteractContainers(),
                    messages.format("count.container-interactions"));

            parts.add(actionButton(
                    messages.format("minecraft.warning.button.trust"),
                    ChatColor.GREEN,
                    "/ttrust " + interactorPlayer,
                    messages.format("minecraft.warning.button.trust-hover", "interactor", interactorPlayer)));
            parts.add(plain(ChatColor.WHITE, " "));
            parts.add(actionButton(
                    messages.format("minecraft.warning.button.report"),
                    ChatColor.RED,
                    "/treport " + interactorPlayer,
                    messages.format("minecraft.warning.button.report-hover", "interactor", interactorPlayer)));
            parts.add(newline());
            if (i < counts.size() - 1) {
                parts.add(newline());
            }
        }

        if (remainingInteractions > 0) {
            parts.add(newline());
            appendLine(parts, ChatColor.GRAY, "... and " + remainingInteractions + " more interaction records.");
        }

        return parts.toArray(new BaseComponent[0]);
    }

    private void appendCountLine(List<BaseComponent> parts, int value, String label) {
        if (value <= 0) {
            return;
        }
        appendLine(
                parts,
                ChatColor.WHITE,
                messages.format("minecraft.warning.count-line", "count", value, "label", label));
    }

    private TextComponent actionButton(String label, ChatColor color, String command, String hover) {
        TextComponent button = new TextComponent(label);
        button.setColor(color.asBungee());
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hover).color(ChatColor.GRAY.asBungee()).create()));
        return button;
    }

    private void appendPlainLines(List<BaseComponent> parts, ChatColor color, String text) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty() || i < lines.length - 1) {
                appendLine(parts, color, lines[i]);
            }
        }
    }

    private void appendLine(List<BaseComponent> parts, ChatColor color, String text) {
        parts.add(plain(color, text));
        parts.add(newline());
    }

    private TextComponent plain(ChatColor color, String text) {
        TextComponent component = new TextComponent(text);
        component.setColor(color.asBungee());
        return component;
    }

    private TextComponent newline() {
        return new TextComponent("\\n");
    }

    private List<NamedRegion> loadNamedRegions() {
        try {
            return regions.findAll();
        } catch (SQLException e) {
            return List.of();
        }
    }

    private String formatBox(MessageBox box, List<NamedRegion> namedRegions, int mergeDistance) {
        String world = formatWorld(box.getWorldId());
        String matchingNames = matchingNamedRegionLabels(box, namedRegions, mergeDistance);

        if (matchingNames.isEmpty()) {
            return messages.format(
                    "region.at",
                    "world", world,
                    "x", box.centerX(),
                    "z", box.centerZ(),
                    "radius", box.displayRadius());
        }
        return messages.format(
                "region.at-named",
                "name", matchingNames,
                "world", world,
                "x", box.centerX(),
                "z", box.centerZ(),
                "radius", box.displayRadius());
    }

    private String matchingNamedRegionLabels(MessageBox interactionArea, List<NamedRegion> namedRegions, int mergeDistance) {
        return namedRegions.stream()
                .filter(named -> interactionArea.isWithinMergeDistanceOf(named.toMessageBox(), mergeDistance))
                .map(NamedRegion::getName)
                .collect(Collectors.joining(" / "));
    }

    private String formatWorld(int wid) {
        String worldName = null;
        try {
            worldName = coreProtect.findWorldName(wid);
        } catch (SQLException ignored) {
            // fall through
        }
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
        return messages.format("world.custom", "name", worldName);
    }
}

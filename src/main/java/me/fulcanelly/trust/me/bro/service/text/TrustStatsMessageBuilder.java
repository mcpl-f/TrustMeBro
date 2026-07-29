package me.fulcanelly.trust.me.bro.service.text;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousPlayerStat;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

/**
 * Formats /truststats Telegram reply.
 *
 * When a player has more than {@link #NAMES_THRESHOLD} trusters/reporters,
 * show the count; otherwise list the (already limited) names.
 *
 * ---
 * Топ 3 самых доверенных:
 * - Steve, доверяют Alice, Bob
 * - Alex, доверяют 12
 *
 * Топ 3 подозрительных:
 * - Herozero, взаимодействий 7582, владельцев 332
 * ^ в регионах «а» / «б» — и 5 других
 * - I_dead_to_lol, взаимодействий 5584, владельцев 257
 * - M1laxa, взаимодействий 2275, владельцев 155
 * ^ в регионах «а» / «б» / «в»
 *
 * Топ 3 по жалобам:
 * - Griefer, пожаловались Alice, Bob, Carol
 * - Scout, пожаловались 7
 * ---
 */
@RequiredArgsConstructor
public final class TrustStatsMessageBuilder {

    public static final int NAMES_THRESHOLD = 3;
    /** How many named regions to list before “and N others”. */
    public static final int REGIONS_DISPLAY_LIMIT = 2;

    private final LocalizationService messages;

    public String build(
            int topSize,
            List<PeopleLine> trusted,
            List<SuspiciousLine> suspicious,
            List<PeopleLine> reported //
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append(messages.format("telegram.command.truststats.trusted-header", "n", topSize))
                .append('\n');
        appendPeopleSection(
                builder,
                trusted,
                "telegram.command.truststats.trusted-by-count",
                "telegram.command.truststats.trusted-by-names");
        builder.append('\n');

        builder.append(messages.format("telegram.command.truststats.suspicious-header", "n", topSize))
                .append('\n');
        appendSuspiciousSection(builder, suspicious);
        builder.append('\n');

        builder.append(messages.format("telegram.command.truststats.reported-header", "n", topSize))
                .append('\n');
        appendPeopleSection(
                builder,
                reported,
                "telegram.command.truststats.reported-by-count",
                "telegram.command.truststats.reported-by-names");

        return builder.toString().trim();
    }

    private void appendSuspiciousSection(StringBuilder builder, List<SuspiciousLine> rows) {
        if (rows.isEmpty()) {
            builder.append(messages.format("telegram.command.truststats.empty")).append('\n');
            return;
        }
        for (SuspiciousLine row : rows) {
            SuspiciousPlayerStat stat = row.getStat();
            builder.append(messages.format(
                    "telegram.command.truststats.suspicious-line",
                    "player", escape(stat.getPlayer()),
                    "interactions", stat.getInteractionsSum(),
                    "owners", stat.getOwnersInvolvedCount()))
                    .append('\n');

            if (row.getTotalRegions() <= 0 || row.getRegionNames().isEmpty()) {
                continue;
            }

            String names = row.getRegionNames().stream()
                    .map(name -> "«" + escape(name) + "»")
                    .collect(Collectors.joining(" / "));
            int others = row.getTotalRegions() - row.getRegionNames().size();
            if (others > 0) {
                builder.append(messages.format(
                        "telegram.command.truststats.suspicious-regions-more",
                        "names", names,
                        "others", others));
            } else {
                builder.append(messages.format(
                        "telegram.command.truststats.suspicious-regions",
                        "names", names));
            }
            builder.append('\n');
        }
    }

    private void appendPeopleSection(
            StringBuilder builder,
            List<PeopleLine> rows,
            String countKey,
            String namesKey //
    ) {
        if (rows.isEmpty()) {
            builder.append(messages.format("telegram.command.truststats.empty")).append('\n');
            return;
        }
        for (PeopleLine row : rows) {
            if (row.getPeopleCount() > NAMES_THRESHOLD) {
                builder.append(messages.format(
                        countKey,
                        "player", escape(row.getPlayer()),
                        "count", row.getPeopleCount()));
            } else {
                builder.append(messages.format(
                        namesKey,
                        "player", escape(row.getPlayer()),
                        "names", escape(String.join(", ", row.getPeopleNames()))));
            }
            builder.append('\n');
        }
    }

    private String escape(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`");
    }

    @Value
    public static class PeopleLine {
        String player;
        int peopleCount;
        List<String> peopleNames;
    }

    @Value
    public static class SuspiciousLine {
        SuspiciousPlayerStat stat;
        List<String> regionNames;
        int totalRegions;
    }
}

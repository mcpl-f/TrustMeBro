package me.fulcanelly.trust.me.bro.service.text;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;

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
 * - Griefer, взаимодействий 140, владельцев 8
 * - Miner, взаимодействий 40, владельцев 2
 *
 * Топ 3 по жалобам:
 * - Griefer, пожаловались Alice, Bob, Carol
 * - Scout, пожаловались 7
 * ---
 */
@RequiredArgsConstructor
public final class TrustStatsMessageBuilder {

    public static final int NAMES_THRESHOLD = 3;

    private final LocalizationService messages;

    public String build(
            int topSize,
            List<PeopleLine> trusted,
            List<SuspiciousPlayerStat> suspicious,
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
        if (suspicious.isEmpty()) {
            builder.append(messages.format("telegram.command.truststats.empty")).append('\n');
        } else {
            for (SuspiciousPlayerStat row : suspicious) {
                builder.append(messages.format(
                        "telegram.command.truststats.suspicious-line",
                        "player", escape(row.getPlayer()),
                        "interactions", row.getInteractionsSum(),
                        "owners", row.getOwnersInvolvedCount()))
                        .append('\n');
            }
        }
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
}

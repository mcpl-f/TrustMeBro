package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.ReportRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder.PeopleLine;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

import org.bukkit.plugin.Plugin;

/**
 * /truststats — top trusted, suspicious, and reported players.
 */
@RequiredArgsConstructor
public final class TrustStatsCommand {

    private static final Pattern COMMAND = Pattern.compile("^/truststats(@\\S+)?(\\s.*)?$");
    private static final int DEFAULT_TOP_SIZE = 3;

    private final Plugin plugin;
    private final TrustRepository trust;
    private final InteractionCountsRepository interactionCounts;
    private final ReportRepository reports;
    private final TrustStatsMessageBuilder messageBuilder;
    private final LocalizationService messages;
    private final Logger logger;

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        int topSize = Math.max(1, plugin.getConfig().getInt("telegram.stats-top-size", DEFAULT_TOP_SIZE));

        try {
            var trustedLines = loadTrustedLines(topSize);
            var suspiciousLines = interactionCounts.findTopSuspicious(topSize);
            var reportedLines = loadReportedLines(topSize);

            String reply = messageBuilder.build(
                    topSize,
                    trustedLines,
                    suspiciousLines,
                    reportedLines);

            event.getMessage().reply(reply);
        } catch (SQLException e) {
            logger.warning("truststats failed: " + e.getMessage());
            event.getMessage().reply(messages.format("telegram.command.truststats.failed"));
        }
    }

    private List<PeopleLine> loadTrustedLines(int topSize) throws SQLException {
        List<PeopleLine> lines = new ArrayList<>();

        for (PeopleAggregateStat row : trust.findTopTrusted(topSize)) {
            List<String> names = row.getPeopleCount() <= TrustStatsMessageBuilder.NAMES_THRESHOLD
                    ? trust.findTrusters(row.getPlayer(), TrustStatsMessageBuilder.NAMES_THRESHOLD)
                    : List.of();
            lines.add(new PeopleLine(row.getPlayer(), row.getPeopleCount(), names));
        }
        return lines;
    }

    private List<PeopleLine> loadReportedLines(int topSize) throws SQLException {
        List<PeopleLine> lines = new ArrayList<>();
        for (PeopleAggregateStat row : reports.findTopReported(topSize)) {
            List<String> names = row.getPeopleCount() <= TrustStatsMessageBuilder.NAMES_THRESHOLD
                    ? reports.findReporters(row.getPlayer(), TrustStatsMessageBuilder.NAMES_THRESHOLD)
                    : List.of();
            lines.add(new PeopleLine(row.getPlayer(), row.getPeopleCount(), names));
        }
        return lines;
    }
}

package me.fulcanelly.trust.me.bro.listener.telegram;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.eventbus.Subscribe;

import me.fulcanelly.tgbridge.tapi.events.CommandEvent;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegionHits;
import me.fulcanelly.trust.me.bro.database.repository.model.PeopleAggregateStat;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousPlayerStat;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder.PeopleLine;
import me.fulcanelly.trust.me.bro.service.text.TrustStatsMessageBuilder.SuspiciousLine;

/**
 * /truststats — top trusted, suspicious, and reported players.
 */
public final class TrustStatsCommand {

    private static final Pattern COMMAND = Pattern.compile("^/truststats(@\\S+)?(\\s.*)?$");
    private static final int DEFAULT_TOP_SIZE = 3;

    private final AppContext context;
    private final TrustStatsMessageBuilder messageBuilder;

    public TrustStatsCommand(AppContext context) {
        this.context = context;
        this.messageBuilder = new TrustStatsMessageBuilder(context.getMessages());
    }

    @Subscribe
    public void onCommand(CommandEvent event) {
        String text = event.getMessage().getText();
        if (text == null || !COMMAND.matcher(text).matches()) {
            return;
        }

        int topSize = Math.max(1, context.getPlugin().getConfig().getInt("telegram.stats-top-size", DEFAULT_TOP_SIZE));
        int mergeDistance = Math.max(
                0,
                context.getPlugin().getConfig().getInt("detection.split-by-regions.merge-distance", 500));

        try {
            var trustedLines = loadTrustedLines(topSize);
            var suspiciousLines = loadSuspiciousLines(topSize, mergeDistance);
            var reportedLines = loadReportedLines(topSize);

            String reply = messageBuilder.build(
                    topSize,
                    trustedLines,
                    suspiciousLines,
                    reportedLines);
            event.getMessage().reply(reply);
        } catch (SQLException e) {
            context.getLogger().warning("truststats failed: " + e.getMessage());
            event.getMessage().reply(context.getMessages().format("telegram.command.truststats.failed"));
        }
    }

    private List<PeopleLine> loadTrustedLines(int topSize) throws SQLException {
        var trust = context.getRepositories().getTrust();
        List<PeopleLine> lines = new ArrayList<>();
        for (PeopleAggregateStat row : trust.findTopTrusted(topSize)) {
            List<String> names = trust.findTrusters(
                    row.getPlayer(),
                    TrustStatsMessageBuilder.NAMES_THRESHOLD);
            lines.add(new PeopleLine(row.getPlayer(), row.getPeopleCount(), names));
        }
        return lines;
    }

    private List<SuspiciousLine> loadSuspiciousLines(int topSize, int mergeDistance) throws SQLException {
        var interactionCounts = context.getRepositories().getInteractionCounts();
        List<SuspiciousLine> lines = new ArrayList<>();
        for (SuspiciousPlayerStat row : interactionCounts.findTopSuspicious(topSize)) {
            NamedRegionHits regions = interactionCounts.findNamedRegionsTouchedByInteractor(
                    row.getPlayer(),
                    mergeDistance,
                    TrustStatsMessageBuilder.REGIONS_DISPLAY_LIMIT);
            lines.add(new SuspiciousLine(row, regions.getNames(), regions.getTotalCount()));
        }
        return lines;
    }

    private List<PeopleLine> loadReportedLines(int topSize) throws SQLException {
        var reports = context.getRepositories().getReports();
        List<PeopleLine> lines = new ArrayList<>();
        for (PeopleAggregateStat row : reports.findTopReported(topSize)) {
            List<String> names = reports.findReporters(
                    row.getPlayer(),
                    TrustStatsMessageBuilder.NAMES_THRESHOLD);
            lines.add(new PeopleLine(row.getPlayer(), row.getPeopleCount(), names));
        }
        return lines;
    }
}

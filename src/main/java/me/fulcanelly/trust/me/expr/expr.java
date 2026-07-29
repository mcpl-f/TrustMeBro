package me.fulcanelly.trust.me.expr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import me.fulcanelly.trust.me.expr.model.DiscoveryResult;
import me.fulcanelly.trust.me.expr.model.RegionBox;
import me.fulcanelly.trust.me.expr.repository.ExprSchemaRepository;
import me.fulcanelly.trust.me.expr.repository.InteractionCountsExprRepository;
import me.fulcanelly.trust.me.expr.repository.NamedRegionsExprRepository;
import me.fulcanelly.trust.me.expr.repository.SuggestedRegionsExprRepository;
import me.fulcanelly.trust.me.expr.service.SuggestedRegionDiscoveryService;
import me.fulcanelly.trust.me.expr.service.SuggestedRegionPrinter;

/**
 * Offline experiment entrypoint: discover + persist suggested regions.
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=me.fulcanelly.trust.me.expr.expr -Dexec.classpathScope=compile
 * </pre>
 */
public class expr {

    private static final int PAGE_SIZE = 5000;
    private static final int MERGE_DISTANCE = 500;
    private static final int TOP_N = 10;
    private static final int NAME_LIMIT = 5;

    public static void main(String[] args) throws Exception {
        Path dbPath = resolveDb(args);
        if (!Files.isRegularFile(dbPath)) {
            System.err.println("DB not found: " + dbPath.toAbsolutePath());
            System.err.println("Pass path, or put database.sqlite3 / .sqlite3 in project root.");
            System.exit(1);
        }
        System.out.println("DB: " + dbPath.toAbsolutePath());
        System.out.println("merge-distance=" + MERGE_DISTANCE + " page=" + PAGE_SIZE);
        System.out.println();

        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            new ExprSchemaRepository(connection).ensureSuggestedRegionTables();

            var namedRepo = new NamedRegionsExprRepository(connection);
            var suggestedRepo = new SuggestedRegionsExprRepository(connection);
            var interactionRepo = new InteractionCountsExprRepository(connection);

            List<RegionBox> named = namedRepo.findAllAsBoxes();
            List<RegionBox> alreadySuggested = suggestedRepo.findAllAsCoverageBoxes();

            var discovery = new SuggestedRegionDiscoveryService(
                    interactionRepo,
                    PAGE_SIZE,
                    MERGE_DISTANCE);

            int outsideNamed = discovery.countNotCovered(named);

            System.out.println("Named regions loaded: " + named.size());
            System.out.println("Existing suggested regions (coverage): " + alreadySuggested.size());
            System.out.println(
                    "Interaction counts outside named regions"
                            + " (geo, merge-distance, ignoring suggested): "
                            + outsideNamed);
            System.out.println();

            List<RegionBox> coverage = new ArrayList<>(named.size() + alreadySuggested.size());
            coverage.addAll(named);
            coverage.addAll(alreadySuggested);

            DiscoveryResult result = discovery.discover(coverage);

            int saved = suggestedRepo.insertNew(result.getNewCandidates());

            System.out.println("Activity boxes scanned: " + result.getScanned());
            System.out.println("Skipped (named / already suggested): " + result.getSkippedCovered());
            System.out.println("New candidates this run: " + result.getNewCandidates().size());
            System.out.println("Inserted suggested_regions: " + saved);
            System.out.println();

            new SuggestedRegionPrinter(NAME_LIMIT).printTop(result.getNewCandidates(), TOP_N);
        }
    }

    private static Path resolveDb(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path a = cwd.resolve("database.sqlite3");
        if (Files.isRegularFile(a)) {
            return a;
        }
        Path b = cwd.resolve(".sqlite3");
        if (Files.isRegularFile(b)) {
            return b;
        }
        return a;
    }
}

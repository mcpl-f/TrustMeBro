package me.fulcanelly.trust.me.expr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import me.fulcanelly.trust.me.expr.model.DiscoveryResult;
import me.fulcanelly.trust.me.expr.model.NamedRegionBox;
import me.fulcanelly.trust.me.expr.model.SuggestedRegion;
import me.fulcanelly.trust.me.expr.repository.ActivityBoxRepository;
import me.fulcanelly.trust.me.expr.repository.ExprSchemaRepository;
import me.fulcanelly.trust.me.expr.repository.NamedRegionRepository;
import me.fulcanelly.trust.me.expr.repository.SuggestedRegionRepository;
import me.fulcanelly.trust.me.expr.service.SuggestedRegionDiscoveryService;
import me.fulcanelly.trust.me.expr.service.SuggestedRegionPrinter;

/**
 * Offline entry: open a DB <b>copy</b>, run Path-B discovery, persist, print top.
 *
 * <p>Vocab: activity box / named region / suggested region — see {@code expr.md}.
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=me.fulcanelly.trust.me.expr.expr -Dexec.classpathScope=compile
 * </pre>
 */
public class expr {

    private static final int PAGE_SIZE = 5000;
    private static final int MERGE_DISTANCE = 100;
    private static final int TOP_N = 10;

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

            var namedRegionRepo = new NamedRegionRepository(connection);
            var suggestedRegionRepo = new SuggestedRegionRepository(connection);
            var activityBoxRepo = new ActivityBoxRepository(connection);

            List<NamedRegionBox> namedRegions = namedRegionRepo.findAll();
            List<SuggestedRegion> existingSuggested = suggestedRegionRepo.findAll();

            var discovery = new SuggestedRegionDiscoveryService(
                    activityBoxRepo,
                    PAGE_SIZE,
                    MERGE_DISTANCE);

            int uncoveredByNamed = discovery.countUncoveredByNamed(namedRegions);

            System.out.println("Named regions loaded: " + namedRegions.size());
            System.out.println("Existing suggested regions: " + existingSuggested.size());
            System.out.println(
                    "Activity boxes outside named regions"
                            + " (merge-distance, ignoring suggested): "
                            + uncoveredByNamed);
            System.out.println();

            DiscoveryResult result = discovery.discover(namedRegions, existingSuggested);

            int dirty = suggestedRegionRepo.persistDirty(result.getDirtySuggested());
            int saved = suggestedRegionRepo.insertNew(result.getNewSuggested());

            System.out.println("Activity boxes scanned: " + result.getScanned());
            System.out.println("Skipped (named): " + result.getSkippedNamed());
            System.out.println("Dirty suggested regions persisted: " + dirty);
            System.out.println("New suggested this run: " + result.getNewSuggested().size());
            System.out.println("Inserted suggested_regions: " + saved);
            System.out.println();

            new SuggestedRegionPrinter().printTop(result.getNewSuggested(), TOP_N);
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

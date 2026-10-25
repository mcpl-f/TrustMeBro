package me.fulcanelly.trust.me.bro.database.repository.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.SqliteJdbi;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.config.MinInteractionThresholds;

public class InteractionMaterialCountsRepositoryTest {

    private static final int WORLD = 1;
    private static final int MERGE = 500;

    private Connection connection;
    private SqliteJdbi database;
    private InteractionWriteRepository writes;
    private PendingInteractionsRepository pending;
    private InteractionMaterialCountsRepository materials;

    @BeforeEach
    void openFreshDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new MigrationRunner(connection).migrate();
        database = new SqliteJdbi(connection);
        writes = new InteractionWriteRepository(database);
        pending = new PendingInteractionsRepository(database);
        materials = new InteractionMaterialCountsRepository(database);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    /*
     * Telegram shows ONE summary for all owner rows of the message.
     *
     *   Griefer -> Alice row:  break minecraft:stone, break minecraft:stone, place minecraft:stone, break minecraft:diamond_block
     *   Griefer -> Bob row:    break minecraft:stone
     *   Griefer -> Carol row:  place minecraft:dirt   (not asked for)
     *
     *   sum(Alice, Bob)
     *     => minecraft:stone         -3 / +1   (4, biggest first)
     *        minecraft:diamond_block -1 / +0
     */
    @Test
    void sumsMaterialsAcrossRowsAndOrdersBiggestFirst() throws Exception {
        long alice = writes.increment("Griefer", "Alice", SuspiciousActionType.BREAK_BLOCK);
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);
        assertEquals(alice, writes.increment("Griefer", "Alice", SuspiciousActionType.BREAK_BLOCK));
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);
        assertEquals(alice, writes.increment("Griefer", "Alice", SuspiciousActionType.PLACE_BLOCK));
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.PLACE_BLOCK);
        materials.increment(alice, "minecraft:diamond_block", SuspiciousActionType.BREAK_BLOCK);

        long bob = writes.increment("Griefer", "Bob", SuspiciousActionType.BREAK_BLOCK);
        materials.increment(bob, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);

        long carol = writes.increment("Griefer", "Carol", SuspiciousActionType.PLACE_BLOCK);
        materials.increment(carol, "minecraft:dirt", SuspiciousActionType.PLACE_BLOCK);

        List<MaterialTotal> totals = materials.sumByInteractionCountIds(List.of(alice, bob));

        assertEquals(
                List.of(new MaterialTotal("minecraft:stone", 3, 1), new MaterialTotal("minecraft:diamond_block", 1, 0)),
                totals);

        // UPDATE ... RETURNING id must still apply the increment, not only return the id.
        InteractionCount aliceRow = pending.findPendingForOwner("Alice").get(0);
        assertEquals(2, aliceRow.getCountBreakBlocks());
        assertEquals(1, aliceRow.getCountPlacedBlocks());
    }

    /*
     * Containers carry no material; no ids means no query at all.
     *
     *   container click -> nothing stored
     *   sum([])         -> []
     */
    @Test
    void containerActionsStoreNothingAndEmptyIdsGiveEmptySummary() throws Exception {
        long row = writes.increment("Griefer", "Alice", SuspiciousActionType.INTERACT_CONTAINER);
        materials.increment(row, "minecraft:chest", SuspiciousActionType.INTERACT_CONTAINER);

        assertTrue(materials.sumByInteractionCountIds(List.of(row)).isEmpty());
        assertTrue(materials.sumByInteractionCountIds(List.of()).isEmpty());
    }

    /*
     * Region path must hand back the id of the row it merged into, otherwise
     * materials would land on the wrong row.
     *
     *   (0,0) Alice   -> row A
     *   (10,10) Alice -> merges into row A   (inside merge=500)
     *   (5000,0) Alice -> new row B          (too far)
     */
    @Test
    void regionIncrementReturnsIdOfMergedOrNewRow() throws Exception {
        long first = writes.incrementInRegion("Griefer", "Alice", WORLD, 0, 0, MERGE, SuspiciousActionType.BREAK_BLOCK);
        long nearby = writes.incrementInRegion("Griefer", "Alice", WORLD, 10, 10, MERGE,
                SuspiciousActionType.BREAK_BLOCK);
        long far = writes.incrementInRegion("Griefer", "Alice", WORLD, 5000, 0, MERGE,
                SuspiciousActionType.BREAK_BLOCK);

        assertEquals(first, nearby);
        assertNotEquals(first, far);
    }

    /*
     * Concurrent events for the same nearby area must share its row.
     *
     *   concurrent calls -> one area row -> every call returns its id
     */
    @Test
    void concurrentRegionIncrementsReuseOneActivityRow() throws Exception {
        int callers = 8;
        var executor = Executors.newFixedThreadPool(callers);
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var results = new ArrayList<java.util.concurrent.Future<Long>>();

        try {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start region increments");
                    }
                    return writes.incrementInRegion(
                            "Griefer", "Alice", WORLD, 10, 10, MERGE, SuspiciousActionType.BREAK_BLOCK);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            long rowId = results.get(0).get(5, TimeUnit.SECONDS);
            for (var result : results) {
                assertEquals(rowId, result.get(5, TimeUnit.SECONDS));
            }

            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM interaction_counts
                    WHERE interactor_player = 'Griefer'
                      AND owner = 'Alice'
                      AND wid = 1
                    """);
                    var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /*
     * Pending rows flow through eligibility, selection, skip, and sent-message attachment.
     *
     *   below threshold -> skipped
     *   eligible row    -> ready -> selected -> attached to notification -> no longer pending
     */
    @Test
    void pendingQueriesApplyThresholdsAndUpdateNotificationState() throws Exception {
        long low = writes.increment("Low", "Alice", SuspiciousActionType.BREAK_BLOCK);
        long ready = writes.increment("Ready", "Alice", SuspiciousActionType.BREAK_BLOCK);
        writes.increment("Ready", "Alice", SuspiciousActionType.BREAK_BLOCK);
        MinInteractionThresholds thresholds = new MinInteractionThresholds(true, 10, 2, 1);

        assertTrue(pending.findPendingByInteractor("Low", thresholds).isEmpty());
        assertEquals(1, pending.findPendingByInteractor("Ready", thresholds).size());
        assertEquals(2, pending.findPendingForOwner("Alice").size());
        assertEquals(List.of("Ready"), pending.findInteractorsReadyForNotification(0, thresholds));
        assertEquals(ready, pending.findTopPendingInteractionForOwner("Alice", "biggest").orElseThrow().getId());

        pending.markSkippedByIds(List.of(low), "test");
        assertEquals(List.of("Ready"), pending.findInteractorsReadyForNotification(0, thresholds));

        long notificationId = new NotificationRepository(database).insertTelegram("Ready", 123L, null);
        pending.attachNotification("Ready", List.of("Alice"), notificationId);
        assertTrue(pending.findPendingForOwner("Alice").isEmpty());
    }
}

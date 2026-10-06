package me.fulcanelly.trust.me.bro.database.repository.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;
import me.fulcanelly.trust.me.bro.database.repository.model.MaterialTotal;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

public class InteractionMaterialCountsRepositoryTest {

    private static final int WORLD = 1;
    private static final int MERGE = 500;

    private Connection connection;
    private InteractionCountsRepository counts;
    private InteractionMaterialCountsRepository materials;

    @BeforeEach
    void openFreshDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new MigrationRunner(connection).migrate();
        counts = new InteractionCountsRepository(connection);
        materials = new InteractionMaterialCountsRepository(connection);
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
        long alice = counts.increment("Griefer", "Alice", SuspiciousActionType.BREAK_BLOCK);
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);
        assertEquals(alice, counts.increment("Griefer", "Alice", SuspiciousActionType.BREAK_BLOCK));
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);
        assertEquals(alice, counts.increment("Griefer", "Alice", SuspiciousActionType.PLACE_BLOCK));
        materials.increment(alice, "minecraft:stone", SuspiciousActionType.PLACE_BLOCK);
        materials.increment(alice, "minecraft:diamond_block", SuspiciousActionType.BREAK_BLOCK);

        long bob = counts.increment("Griefer", "Bob", SuspiciousActionType.BREAK_BLOCK);
        materials.increment(bob, "minecraft:stone", SuspiciousActionType.BREAK_BLOCK);

        long carol = counts.increment("Griefer", "Carol", SuspiciousActionType.PLACE_BLOCK);
        materials.increment(carol, "minecraft:dirt", SuspiciousActionType.PLACE_BLOCK);

        List<MaterialTotal> totals = materials.sumByInteractionCountIds(List.of(alice, bob));

        assertEquals(
                List.of(new MaterialTotal("minecraft:stone", 3, 1), new MaterialTotal("minecraft:diamond_block", 1, 0)),
                totals);

        // UPDATE ... RETURNING id must still apply the increment, not only return the id.
        InteractionCount aliceRow = counts.findPendingForOwner("Alice").get(0);
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
        long row = counts.increment("Griefer", "Alice", SuspiciousActionType.INTERACT_CONTAINER);
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
        long first = counts.incrementInRegion("Griefer", "Alice", WORLD, 0, 0, MERGE, SuspiciousActionType.BREAK_BLOCK);
        long nearby = counts.incrementInRegion("Griefer", "Alice", WORLD, 10, 10, MERGE,
                SuspiciousActionType.BREAK_BLOCK);
        long far = counts.incrementInRegion("Griefer", "Alice", WORLD, 5000, 0, MERGE,
                SuspiciousActionType.BREAK_BLOCK);

        assertEquals(first, nearby);
        assertNotEquals(first, far);
    }
}

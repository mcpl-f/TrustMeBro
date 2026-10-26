package me.fulcanelly.trust.me.bro.service.activitybox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

public class ActivityBoxMessageGrouperTest {

    private static final int WORLD = 1;
    private static final int OTHER_WORLD = 2;
    private static final int MERGE = 500;

    /*
     * Two owners, same point, same world — must become one Telegram block.
     *
     *   merge=500
     *
     *   (0,0) Alice
     *   (0,0) Bob
     *
     *   => one group, bounds still a point, members [Alice, Bob]
     */
    @Test
    void samePointSameWorldMergesIntoOneGroup() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("Alice", WORLD, 0, 0),
                        at("Bob", WORLD, 0, 0)),
                MERGE);

        assertEquals(1, groups.size());
        assertEquals(List.of("Alice", "Bob"), owners(groups.get(0)));
        assertEquals(0, groups.get(0).getActivityBox().getMinX());
        assertEquals(0, groups.get(0).getActivityBox().getMaxX());
    }

    /*
     * Close enough on XZ (inside merge pad) — one group, box expands.
     *
     *   A ----100---- B          merge=500
     *   (0,0)       (100,50)
     *
     *   |<------ within 500 ------>|
     *
     *   => one group, united box [0..100] x [0..50]
     */
    @Test
    void nearbyBoxesWithinMergeDistanceUniteAndExpandBounds() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("A", WORLD, 0, 0),
                        at("B", WORLD, 100, 50)),
                MERGE);

        assertEquals(1, groups.size());
        MessageBox box = groups.get(0).getActivityBox();
        assertEquals(0, box.getMinX());
        assertEquals(0, box.getMinZ());
        assertEquals(100, box.getMaxX());
        assertEquals(50, box.getMaxZ());
        assertEquals(List.of("A", "B"), owners(groups.get(0)));
    }

    /*
     * Farther than merge distance — stay separate blocks.
     *
     *   A                         B
     *   (0,0)  ---- 2000 ----   (2000,2000)
     *
     *   merge=500  =>  gap too big
     *
     *   => two groups
     */
    @Test
    void farBoxesStaySeparateGroups() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("A", WORLD, 0, 0),
                        at("B", WORLD, 2000, 2000)),
                MERGE);

        assertEquals(2, groups.size());
        assertEquals(List.of("A"), owners(groups.get(0)));
        assertEquals(List.of("B"), owners(groups.get(1)));
    }

    /*
     * Chain: A near B, B near C, A not directly near C — still one group via glue.
     *
     *   merge=500
     *
     *   A          B          C
     *   0 ------ 400 ------ 800
     *
     *   A--B ok, B--C ok, A--C would be 800 > 500 alone,
     *   but C joins B's cluster which already has A.
     *
     *   => one group [A,B,C], box 0..800
     */
    @Test
    void transitiveNearbyChainGluesIntoOneGroup() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("A", WORLD, 0, 0),
                        at("B", WORLD, 400, 0),
                        at("C", WORLD, 800, 0)),
                MERGE);

        assertEquals(1, groups.size());
        assertEquals(List.of("A", "B", "C"), owners(groups.get(0)));
        assertEquals(0, groups.get(0).getActivityBox().getMinX());
        assertEquals(800, groups.get(0).getActivityBox().getMaxX());
    }

    /*
     * Same coords, different CoreProtect world id — must NOT merge.
     *
     *   world=1 (0,0) Alice
     *   world=2 (0,0) Bob
     *
     *   => two groups
     */
    @Test
    void sameCoordsDifferentWorldStaySeparate() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("Alice", WORLD, 0, 0),
                        at("Bob", OTHER_WORLD, 0, 0)),
                MERGE);

        assertEquals(2, groups.size());
        assertEquals(WORLD, groups.get(0).getActivityBox().getWorldId());
        assertEquals(OTHER_WORLD, groups.get(1).getActivityBox().getWorldId());
    }

    /*
     * No activity box columns — never united, even if "close" conceptually.
     *
     *   Alice  (no box)
     *   Bob    (no box)
     *
     *   => two singleton groups, activityBox == null
     */
    @Test
    void interactionsWithoutBoxNeverMerge() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        noBox("Alice"),
                        noBox("Bob")),
                MERGE);

        assertEquals(2, groups.size());
        assertFalse(groups.get(0).hasActivityBox());
        assertFalse(groups.get(1).hasActivityBox());
        assertNull(groups.get(0).getActivityBox());
        assertEquals(List.of("Alice"), owners(groups.get(0)));
        assertEquals(List.of("Bob"), owners(groups.get(1)));
    }

    /*
     * Mix: legacy rows first as singletons, then a merged box cluster.
     *
     *   Legacy1 (no box)
     *   Legacy2 (no box)
     *   NearA (0,0) + NearB (10,10)  -> one box group
     *
     *   => [Legacy1] [Legacy2] [NearA+NearB]
     */
    @Test
    void mixedLegacyAndBoxedPutsLegacySingletonsBeforeClusters() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        noBox("Legacy1"),
                        at("NearA", WORLD, 0, 0),
                        noBox("Legacy2"),
                        at("NearB", WORLD, 10, 10)),
                MERGE);

        assertEquals(3, groups.size());

        assertEquals(List.of("Legacy1"), owners(groups.get(0)));
        assertTrue(!groups.get(0).hasActivityBox());

        assertEquals(List.of("Legacy2"), owners(groups.get(1)));
        assertTrue(!groups.get(1).hasActivityBox());

        assertTrue(groups.get(2).hasActivityBox());
        assertEquals(List.of("NearA", "NearB"), owners(groups.get(2)));
    }

    /*
     * mergeDistance=0 — only overlapping / touching boxes unite, not "nearby".
     *
     *   (0,0) and (1,0) with merge=0 do not pad, so they stay apart
     *   (unless boxes already overlap — here both are points).
     */
    @Test
    void zeroMergeDistanceDoesNotPullApartPointsTogether() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("A", WORLD, 0, 0),
                        at("B", WORLD, 1, 0)),
                0);

        assertEquals(2, groups.size());
    }

    /*
     * Two separate neighborhoods — two Telegram box headers.
     *
     *   Cluster west:  (0,0) (50,0)
     *   Cluster east:  (5000,0) (5050,0)
     *
     *   merge=500
     *
     *   west ----far---- east
     *
     *   => two groups
     */
    @Test
    void twoDistantNeighborhoodsBecomeTwoGroups() {
        List<ActivityBoxOwnerGroup> groups = ActivityBoxMessageGrouper.uniteAndGroupByBox(
                List.of(
                        at("W1", WORLD, 0, 0),
                        at("E1", WORLD, 5000, 0),
                        at("W2", WORLD, 50, 0),
                        at("E2", WORLD, 5050, 0)),
                MERGE);

        assertEquals(2, groups.size());
        assertEquals(List.of("W1", "W2"), owners(groups.get(0)));
        assertEquals(List.of("E1", "E2"), owners(groups.get(1)));
    }

    private static InteractionCount at(String owner, int worldId, int x, int z) {
        return new InteractionCount(
                1L,
                "Griefer",
                owner,
                1,
                0,
                0,
                worldId,
                x,
                z,
                x,
                z);
    }

    private static InteractionCount noBox(String owner) {
        return new InteractionCount(
                1L,
                "Griefer",
                owner,
                1,
                0,
                0,
                null,
                null,
                null,
                null,
                null);
    }

    private static List<String> owners(ActivityBoxOwnerGroup group) {
        return group.getInteractions().stream()
                .map(InteractionCount::getOwnerPlayer)
                .collect(Collectors.toList());
    }
}

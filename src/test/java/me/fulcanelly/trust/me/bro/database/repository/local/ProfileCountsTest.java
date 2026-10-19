package me.fulcanelly.trust.me.bro.database.repository.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.fulcanelly.trust.me.bro.database.MigrationRunner;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;

/**
 * The numbers behind /rep.
 */
public class ProfileCountsTest {

    private Connection connection;
    private TrustRepository trust;
    private ReportRepository reports;
    private InteractionCountsRepository interactionCounts;

    @BeforeEach
    void openFreshDatabase() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new MigrationRunner(connection).migrate();
        trust = new TrustRepository(connection);
        reports = new ReportRepository(connection);
        interactionCounts = new InteractionCountsRepository(connection);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    /*
     * Alice looks at her own profile.
     *
     *   touched Alice's stuff:  Bob (2 rows: no region + region), Carol, Dave, Eve
     *   Alice decided:          trusts Carol, reported Dave
     *   others decided:         Frank trusts Alice, Gina and Hank reported Alice
     *
     *   => Alice's decisions:  trusted 1, reported 1
     *      reputation:         trusters 1, reporters 2
     *      pending:            2 players (Bob, Eve), Bob counts once
     *
     *   Bob's own profile has nobody waiting: nobody touched Bob's stuff.
     */
    @Test
    void countsDecisionsReputationAndPlayersStillWaiting() throws Exception {
        interactionCounts.increment("Bob", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.incrementInRegion("Bob", "Alice", 1, 0, 0, 500, SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Carol", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Dave", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Eve", "Alice", SuspiciousActionType.BREAK_BLOCK);

        trust.trust("Alice", "Carol", null);
        reports.report("Alice", null, "Dave", "Alice");
        trust.trust("Frank", "Alice", null);
        reports.report("Gina", null, "Alice", "Gina");
        reports.report("Hank", null, "Alice", "Hank");

        assertEquals(1, trust.countTrusted("Alice"));
        assertEquals(1, reports.countReportedByOwner("Alice"));
        assertEquals(1, trust.countTrusters("Alice"));
        assertEquals(2, reports.countReporters("Alice"));
        assertEquals(2, interactionCounts.countUndecidedInteractors("Alice"));

        assertEquals(0, interactionCounts.countUndecidedInteractors("Bob"));
    }

    /*
     * The lists behind the profile buttons, for Alice.
     *
     *   Alice trusts:     Carol, Dan        -> "You trust"
     *   Alice reported:   Dave              -> "You reported"
     *   trust Alice:      Frank             -> "Trust you"
     *   reported Alice:   Gina, Hank        -> "Reported you"
     *   touched her stuff, undecided: Bob (twice), Eve, and Carol/Dave who she already decided on
     *
     *   => waiting for her decision: Bob, Eve (alphabetical, Bob once)
     *   => a limit cuts the list, an offset skips rows of it, neither reorders it
     */
    @Test
    void listsPlayersBehindEachProfileCounter() throws Exception {
        interactionCounts.increment("Bob", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.incrementInRegion("Bob", "Alice", 1, 0, 0, 500, SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Eve", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Carol", "Alice", SuspiciousActionType.BREAK_BLOCK);
        interactionCounts.increment("Dave", "Alice", SuspiciousActionType.BREAK_BLOCK);

        trust.trust("Alice", "Carol", null);
        trust.trust("Alice", "Dan", null);
        reports.report("Alice", null, "Dave", "Alice");
        trust.trust("Frank", "Alice", null);
        reports.report("Gina", null, "Alice", "Gina");
        reports.report("Hank", null, "Alice", "Hank");

        assertEquals(List.of("Carol", "Dan"), trust.findTrusted("Alice", 10, 0));
        assertEquals(List.of("Dave"), reports.findReportedByOwner("Alice", 10, 0));
        assertEquals(List.of("Frank"), trust.findTrusters("Alice", 10, 0));
        assertEquals(List.of("Gina", "Hank"), reports.findReporters("Alice", 10, 0));
        assertEquals(List.of("Bob", "Eve"), interactionCounts.findUndecidedInteractors("Alice", 10, 0));

        assertEquals(List.of("Bob"), interactionCounts.findUndecidedInteractors("Alice", 1, 0));
        assertEquals(List.of("Eve"), interactionCounts.findUndecidedInteractors("Alice", 1, 1));
    }
}

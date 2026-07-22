package me.fulcanelly.trust.me.bro.service;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.fulcanelly.trust.me.bro.database.repository.coreprotect.CoreProtectReadRepository;
import me.fulcanelly.trust.me.bro.database.repository.model.SuspiciousActionType;
import me.fulcanelly.trust.me.bro.database.repository.local.InteractionCountsRepository;
import me.fulcanelly.trust.me.bro.database.repository.local.TrustRepository;

@RequiredArgsConstructor
public final class SuspicionDetectionService {

    private final CoreProtectReadRepository coreProtect;
    private final TrustRepository trustRepository;
    private final InteractionCountsRepository interactionCounts;
    private final Logger logger;

    public void recordBlockAction(Player interactor, Location location, SuspiciousActionType actionType) {
        try {
            record(interactor.getName(), coreProtect.findBlockOwners(location), actionType);
        } catch (SQLException e) {
            logger.warning("CoreProtect block lookup failed: " + e.getMessage());
        }
    }

    public void recordContainerAction(Player interactor, Location location) {
        try {
            record(interactor.getName(), coreProtect.findContainerOwners(location), SuspiciousActionType.INTERACT_CONTAINER);
        } catch (SQLException e) {
            logger.warning("CoreProtect container lookup failed: " + e.getMessage());
        }
    }

    private void record(String interactorPlayer, Set<String> owners, SuspiciousActionType actionType) throws SQLException {
        for (String owner : owners) {
            if (owner.equalsIgnoreCase(interactorPlayer)) {
                continue;
            }
            if (trustRepository.isTrusted(owner, interactorPlayer)) {
                continue;
            }
            interactionCounts.increment(interactorPlayer, owner, actionType);
        }
    }
}

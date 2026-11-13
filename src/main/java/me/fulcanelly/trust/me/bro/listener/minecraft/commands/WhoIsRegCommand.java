package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;
import me.fulcanelly.trust.me.bro.database.repository.model.NamedRegion;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class WhoIsRegCommand implements CommandExecutor {

    private static final double MAX_DISTANCE = 500;
    private static final int MAX_RESULTS = 5;

    private final AppContext context;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = context.getMessages();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.whosereg.players-only"));
            return true;
        }

        try {
            var location = player.getLocation();
            Integer worldId = context.getCoreProtect().findWorldId(location.getWorld().getName());
            if (worldId == null) {
                player.sendMessage(messages.format("command.whosereg.unknown-world"));
                return true;
            }

            int x = location.getBlockX();
            int z = location.getBlockZ();
            List<NamedRegion> regions = context.getRepositories().getRegions().findAll().stream()
                    .filter(region -> region.getWid() == worldId)
                    .collect(Collectors.toList());

            List<NamedRegion> containing = regions.stream()
                    .filter(region -> contains(region, x, z))
                    .collect(Collectors.toList());

            if (!containing.isEmpty()) {
                if (containing.size() > 1) {
                    player.sendMessage(messages.format(
                            "command.whosereg.overlap-header",
                            "count", containing.size()));
                }
                containing.stream()
                        .limit(MAX_RESULTS)
                        .map(this::formatRegion)
                        .forEach(player::sendMessage);

                if (containing.size() > MAX_RESULTS) {
                    player.sendMessage(messages.format(
                            "command.whosereg.more",
                            "count", containing.size() - MAX_RESULTS));
                }
                return true;
            }

            NamedRegion closest = regions.stream()
                    .filter(region -> distanceToAabb(region, x, z) <= MAX_DISTANCE)
                    .min(Comparator.comparingDouble(region -> distanceToAabb(region, x, z)))
                    .orElse(null);

            if (closest == null) {
                player.sendMessage(messages.format("command.whosereg.none"));
                return true;
            }

            player.sendMessage(messages.format(
                    "command.whosereg.closest",
                    "distance", Math.round(distanceToAabb(closest, x, z))));
            player.sendMessage(formatRegion(closest));
        } catch (Exception e) {
            context.getLogger().warning("whosereg failed: " + e.getMessage());
            player.sendMessage(messages.format("command.whosereg.failed"));
        }
        return true;
    }

    private boolean contains(NamedRegion region, int x, int z) {
        long radius = Math.max(0, region.getRadius());
        return x >= (long) region.getCenterX() - radius
                && x <= (long) region.getCenterX() + radius
                && z >= (long) region.getCenterZ() - radius
                && z <= (long) region.getCenterZ() + radius;
    }

    private double distanceToAabb(NamedRegion region, int x, int z) {
        long radius = Math.max(0, region.getRadius());
        long minX = (long) region.getCenterX() - radius;
        long maxX = (long) region.getCenterX() + radius;
        long minZ = (long) region.getCenterZ() - radius;
        long maxZ = (long) region.getCenterZ() + radius;
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.hypot(dx, dz);
    }

    private String formatRegion(NamedRegion region) {
        long radius = Math.max(0, region.getRadius());
        return context.getMessages().format(
                "command.whosereg.region",
                "id", region.getId(),
                "name", region.getName(),
                "mutable", region.isMutable(),
                "creator", region.getCreatedByMcName(),
                "x", region.getCenterX(),
                "z", region.getCenterZ(),
                "radius", region.getRadius(),
                "minX", (long) region.getCenterX() - radius,
                "minZ", (long) region.getCenterZ() - radius,
                "maxX", (long) region.getCenterX() + radius,
                "maxZ", (long) region.getCenterZ() + radius);
    }
}

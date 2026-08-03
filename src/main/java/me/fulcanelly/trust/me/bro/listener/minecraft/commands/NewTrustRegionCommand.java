package me.fulcanelly.trust.me.bro.listener.minecraft.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.trust.me.bro.bootstrap.AppContext;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * /new_trust_region &lt;radius&gt; &lt;name...&gt;
 * Creates a named region at the player's current block position.
 */
@RequiredArgsConstructor
public final class NewTrustRegionCommand implements CommandExecutor {

    private final AppContext context;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = context.getMessages();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("command.new-trust-region.players-only"));
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.format("command.new-trust-region.bad-radius"));
            return true;
        }
        if (radius < 0) {
            player.sendMessage(messages.format("command.new-trust-region.bad-radius"));
            return true;
        }

        String name = Arrays.stream(args, 1, args.length).collect(Collectors.joining(" ")).trim();
        if (name.isEmpty()) {
            return false;
        }

        var location = player.getLocation();
        String worldName = location.getWorld().getName();
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();

        try {
            Integer wid = context.getCoreProtect().findWorldId(worldName);
            if (wid == null) {
                player.sendMessage(messages.format("command.new-trust-region.unknown-world"));
                return true;
            }
            long id = context.getRepositories().getRegions().insert(
                    name,
                    true,
                    player.getName(),
                    wid,
                    centerX,
                    centerZ,
                    radius);

            player.sendMessage(messages.format(
                    "command.new-trust-region.created",
                    "id", id,
                    "name", name,
                    "radius", radius,
                    "x", centerX,
                    "z", centerZ,
                    "world", worldName));
        } catch (Exception e) {
            context.getLogger().warning("new_trust_region failed: " + e.getMessage());
            player.sendMessage(messages.format("command.new-trust-region.failed"));
        }
        return true;
    }
}

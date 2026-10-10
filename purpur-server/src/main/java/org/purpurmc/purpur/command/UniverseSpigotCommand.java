package org.purpurmc.purpur.command;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Item;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.task.UniverseGovernorTask;

/**
 * Administrative and Diagnostics Command for UniverseSpigot (+10,000 Players Engine).
 *
 * Aliases: /universespigot, /uspigot, /universe
 * Author: DevPapo
 */
public class UniverseSpigotCommand extends Command {

    public UniverseSpigotCommand(String name) {
        super(name);
        this.description = "UniverseSpigot 10k Engine diagnostics, profile manager and lag controls";
        this.usageMessage = "/universespigot [status | reload | profile <name> | lagprotection <on|off|auto> | purge <items|ambient>]";
        this.setPermission("bukkit.command.universespigot");
        this.setAliases(Arrays.asList("uspigot", "universe"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        if (args.length == 1) {
            return Stream.of("status", "reload", "profile", "lagprotection", "purge")
                    .filter(arg -> arg.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            return Stream.of("10k-hub", "10k-minigames", "10k-survival")
                    .filter(arg -> arg.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lagprotection")) {
            return Stream.of("on", "off", "auto")
                    .filter(arg -> arg.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("purge")) {
            return Stream.of("items", "ambient", "monsters")
                    .filter(arg -> arg.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            showStatusDashboard(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                UniversePurpurConfig.reload(new File("."));
                sender.sendMessage(Component.text("✔ UniverseSpigot 10k configuration reloaded successfully.", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Active Profile: ", NamedTextColor.GRAY)
                        .append(Component.text(UniversePurpurConfig.profile, NamedTextColor.GOLD)));
            }
            case "profile" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /universespigot profile <10k-hub | 10k-minigames | 10k-survival>", NamedTextColor.RED));
                    return false;
                }
                String targetProfile = args[1].toLowerCase();
                UniversePurpurConfig.profile = targetProfile;
                sender.sendMessage(Component.text("✔ Dynamic profile switched to: ", NamedTextColor.GREEN)
                        .append(Component.text(targetProfile, NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("New settings will take effect immediately across all tracked worlds.", NamedTextColor.GRAY));
            }
            case "lagprotection" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /universespigot lagprotection <on | off | auto>", NamedTextColor.RED));
                    return false;
                }
                String mode = args[1].toLowerCase();
                UniverseGovernorTask governor = UniverseGovernorTask.instance();
                switch (mode) {
                    case "on" -> {
                        governor.setManualEmergencyOverride(true);
                        sender.sendMessage(Component.text("⚠ Emergency Lag Protection forced ON.", NamedTextColor.YELLOW));
                    }
                    case "off" -> {
                        governor.setManualEmergencyOverride(false);
                        sender.sendMessage(Component.text("⚠ Emergency Lag Protection forced OFF.", NamedTextColor.YELLOW));
                    }
                    case "auto" -> {
                        governor.setManualEmergencyOverride(null);
                        sender.sendMessage(Component.text("✔ Emergency Lag Protection set to AUTO (Dynamic MSPT Governor).", NamedTextColor.GREEN));
                    }
                    default -> sender.sendMessage(Component.text("Invalid mode. Use 'on', 'off' or 'auto'.", NamedTextColor.RED));
                }
            }
            case "purge" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /universespigot purge <items | ambient | monsters>", NamedTextColor.RED));
                    return false;
                }
                String target = args[1].toLowerCase();
                if (!target.equals("items") && !target.equals("ambient") && !target.equals("monsters")) {
                    sender.sendMessage(Component.text("Invalid purge target. Use 'items', 'ambient' or 'monsters'.", NamedTextColor.RED));
                    return false;
                }
                int purgedCount = 0;
                for (World world : Bukkit.getWorlds()) {
                    if (target.equals("items")) {
                        for (Item item : world.getEntitiesByClass(Item.class)) {
                            item.remove();
                            purgedCount++;
                        }
                    } else if (target.equals("ambient")) {
                        for (Ambient ambient : world.getEntitiesByClass(Ambient.class)) {
                            ambient.remove();
                            purgedCount++;
                        }
                    } else if (target.equals("monsters")) {
                        for (org.bukkit.entity.Monster monster : world.getEntitiesByClass(org.bukkit.entity.Monster.class)) {
                            monster.remove();
                            purgedCount++;
                        }
                    }
                }
                sender.sendMessage(Component.text("✔ Purged " + purgedCount + " " + target + " across all active worlds.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /" + commandLabel + " status", NamedTextColor.RED));
        }

        return true;
    }

    private void showStatusDashboard(CommandSender sender) {
        UniverseGovernorTask governor = UniverseGovernorTask.instance();
        governor.run(); // Refresh metrics

        double mspt = governor.getCurrentMspt();
        double tps = governor.getCurrentTps();
        NamedTextColor tpsColor = (tps >= 19.5D) ? NamedTextColor.GREEN : (tps >= 17.0D) ? NamedTextColor.YELLOW : NamedTextColor.RED;
        NamedTextColor msptColor = (mspt <= 35.0D) ? NamedTextColor.GREEN : (mspt <= 45.0D) ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long totalMb = runtime.totalMemory() / 1024L / 1024L;
        long maxMb = runtime.maxMemory() / 1024L / 1024L;

        int totalPlayers = Bukkit.getOnlinePlayers().size();
        int totalLoadedChunks = Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum();

        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(" UniverseSpigot 10k Engine Dashboard ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("(by DevPapo)", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text("• TPS: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", tps), tpsColor))
                .append(Component.text(" | MSPT: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.2f ms", mspt), msptColor)));

        sender.sendMessage(Component.text("• Throttle State: ", NamedTextColor.GRAY)
                .append(Component.text(governor.getThrottleLevelName(), (governor.getThrottleLevel() == 0) ? NamedTextColor.GREEN : (governor.getThrottleLevel() <= 2) ? NamedTextColor.YELLOW : NamedTextColor.RED)));

        sender.sendMessage(Component.text("• Active Profile: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.profile, NamedTextColor.AQUA)));

        sender.sendMessage(Component.text("• Online Players: ", NamedTextColor.GRAY)
                .append(Component.text(totalPlayers, NamedTextColor.WHITE))
                .append(Component.text(" | Loaded Chunks: ", NamedTextColor.GRAY))
                .append(Component.text(totalLoadedChunks, NamedTextColor.WHITE)));

        sender.sendMessage(Component.text("• Memory (RAM): ", NamedTextColor.GRAY)
                .append(Component.text(usedMb + " MB", NamedTextColor.WHITE))
                .append(Component.text(" / " + maxMb + " MB (Allocated: " + totalMb + " MB)", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text("• Collisions Cap: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.maxCollisionsPerEntity, NamedTextColor.WHITE))
                .append(Component.text(" | Player Collisions: ", NamedTextColor.GRAY))
                .append(Component.text(UniversePurpurConfig.playerCollisionsEnabled ? "ON" : "OFF", UniversePurpurConfig.playerCollisionsEnabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("• Netty Rate Limiter: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.packetRateLimitEnabled ? (UniversePurpurConfig.maxPacketsPerSecond + " pps (burst " + UniversePurpurConfig.packetBurstLimit + ")") : "DISABLED", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
    }
}

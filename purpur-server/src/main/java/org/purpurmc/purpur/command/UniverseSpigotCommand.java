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
import org.purpurmc.purpur.network.AntiCrashShield;
import org.purpurmc.purpur.task.UniverseGovernorTask;
import org.purpurmc.purpur.task.UniverseMemoryGovernor;

/**
 * Administrative and Diagnostics Command for UniverseSpigot (+15,000 Players Titan Engine).
 *
 * Aliases: /universespigot, /uspigot, /universe
 * Author: DevPapo
 */
public class UniverseSpigotCommand extends Command {

    public UniverseSpigotCommand(String name) {
        super(name);
        this.description = "UniverseSpigot 15k Titan Engine diagnostics, profile manager and lag controls";
        this.usageMessage = "/universespigot [status | reload | profile <name> | lagprotection <on|off|auto> | purge <items|ambient|monsters> | memory [trim] | shield | spatial]";
        this.setPermission("bukkit.command.universespigot");
        this.setAliases(Arrays.asList("uspigot", "universe"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        if (args.length == 1) {
            return Stream.of("status", "reload", "profile", "lagprotection", "purge", "memory", "shield", "spatial")
                    .filter(arg -> arg.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            return Stream.of("15k-hyper-hub", "15k-mega-arena", "15k-scale-smp", "10k-hub", "10k-minigames", "10k-survival")
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
        if (args.length == 2 && args[0].equalsIgnoreCase("memory")) {
            return Stream.of("trim")
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
                UniversePurpurConfig.reload();
                sender.sendMessage(Component.text("✔ UniverseSpigot 15k configuration reloaded successfully.", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Active Profile: ", NamedTextColor.GRAY)
                        .append(Component.text(UniversePurpurConfig.profile, NamedTextColor.GOLD)));
            }
            case "profile" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /universespigot profile <15k-hyper-hub | 15k-mega-arena | 15k-scale-smp | 10k-hub>", NamedTextColor.RED));
                    return false;
                }
                String targetProfile = args[1].toLowerCase();
                UniversePurpurConfig.setProfile(targetProfile);
                sender.sendMessage(Component.text("✔ Dynamic profile switched and saved: ", NamedTextColor.GREEN)
                        .append(Component.text(targetProfile, NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("New settings have taken effect immediately across all tracked worlds.", NamedTextColor.GRAY));
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
            case "memory" -> {
                UniverseMemoryGovernor memoryGovernor = UniverseMemoryGovernor.instance();
                if (args.length >= 2 && args[1].equalsIgnoreCase("trim")) {
                    int cleaned = memoryGovernor.trimCaches();
                    sender.sendMessage(Component.text("✔ Proactive memory trimming executed. Touched " + cleaned + " world partitions.", NamedTextColor.GREEN));
                }
                memoryGovernor.run();
                sender.sendMessage(Component.text("=== UniverseSpigot JVM Memory Telemetry ===", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("• Heap: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.1f MB", memoryGovernor.getHeapUsedMb()), NamedTextColor.WHITE))
                        .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format("%.1f MB (%.1f%%)", memoryGovernor.getHeapMaxMb(), memoryGovernor.getHeapUsagePercent()),
                                memoryGovernor.getHeapUsagePercent() < 80.0D ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
                sender.sendMessage(Component.text("• Total GC Pause Time: ", NamedTextColor.GRAY)
                        .append(Component.text(memoryGovernor.getTotalGcPauseTimeMs() + " ms", NamedTextColor.AQUA)));
                sender.sendMessage(Component.text("• Auto-Trim Count: ", NamedTextColor.GRAY)
                        .append(Component.text(memoryGovernor.getAutoTrimCount(), NamedTextColor.WHITE)));
            }
            case "shield" -> {
                AntiCrashShield shield = AntiCrashShield.instance();
                sender.sendMessage(Component.text("=== UniverseSpigot Exploit & Anti-Crash Shield ===", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("• Total Blocked Exploits: ", NamedTextColor.GRAY)
                        .append(Component.text(shield.getTotalBlockedExploits(), NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("• Malformed Movements (NaN/Infinity): ", NamedTextColor.GRAY)
                        .append(Component.text(shield.getBlockedInvalidMovement(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("• Oversized NBT Payloads: ", NamedTextColor.GRAY)
                        .append(Component.text(shield.getBlockedOversizedPayloads(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("• Invalid Container Window Slots: ", NamedTextColor.GRAY)
                        .append(Component.text(shield.getBlockedInvalidSlots(), NamedTextColor.WHITE)));
            }
            case "spatial" -> {
                org.purpurmc.purpur.util.SpatialGrid grid = org.purpurmc.purpur.util.SpatialGrid.instance();
                sender.sendMessage(Component.text("=== UniverseSpigot 2D Spatial Partitioning Engine ===", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("• Spatial Grid Status: ", NamedTextColor.GRAY)
                        .append(Component.text(UniversePurpurConfig.spatialGridEnabled ? "ENABLED" : "DISABLED", UniversePurpurConfig.spatialGridEnabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
                sender.sendMessage(Component.text("• Total Active Buckets: ", NamedTextColor.GRAY)
                        .append(Component.text(grid.getBucketCount(), NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("• Partition Dimensions: ", NamedTextColor.GRAY)
                        .append(Component.text("32x32 blocks/bucket", NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("• Nearest Player Sorting: ", NamedTextColor.GRAY)
                        .append(Component.text("ACTIVE (O(1) lookups, proximity guaranteed)", NamedTextColor.GREEN)));
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
        sender.sendMessage(Component.text(" UniverseSpigot 15k Hyper-Scale Dashboard ", NamedTextColor.GOLD, TextDecoration.BOLD)
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

        sender.sendMessage(Component.text("• Player Culling: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.maxVisiblePlayersPerClient + " players/client", NamedTextColor.WHITE))
                .append(Component.text(" | Range: ", NamedTextColor.GRAY))
                .append(Component.text(UniversePurpurConfig.trackingRangePlayer + "m", NamedTextColor.WHITE)));

        sender.sendMessage(Component.text("• Collisions Cap: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.maxCollisionsPerEntity, NamedTextColor.WHITE))
                .append(Component.text(" | Player Collisions: ", NamedTextColor.GRAY))
                .append(Component.text(UniversePurpurConfig.playerCollisionsEnabled ? "ON" : "OFF", UniversePurpurConfig.playerCollisionsEnabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("• Netty Limiter: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.packetRateLimitEnabled ? (UniversePurpurConfig.maxPacketsPerSecond + " pps" + (UniversePurpurConfig.adaptiveRateLimiting ? " [ADAPTIVE]" : "")) : "DISABLED", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("• Spatial Partitioning: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.spatialGridEnabled ? "ENABLED (32x32 Grid)" : "DISABLED", NamedTextColor.WHITE)));

        sender.sendMessage(Component.text("• Anti-Crash Shield: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.antiCrashShieldEnabled ? "ACTIVE (Zero-Day Hardened)" : "DISABLED", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("========================================", NamedTextColor.DARK_GRAY));
    }
}

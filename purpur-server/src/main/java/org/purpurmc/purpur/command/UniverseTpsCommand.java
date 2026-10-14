package org.purpurmc.purpur.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.task.UniverseGovernorTask;

/**
 * Enhanced TPS & Performance Command for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Aliases: /tps, /lag, /mspt
 * Author: DevPapo (admin@scon.host)
 */
public class UniverseTpsCommand extends Command {

    public UniverseTpsCommand(String name) {
        super(name);
        this.description = "Muestra el rendimiento en tiempo real (TPS, MSPT, memoria y gobernador) de UniversePurpur";
        this.usageMessage = "/tps";
        this.setPermission("bukkit.command.tps");
        this.setAliases(Arrays.asList("lag", "mspt"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        UniverseGovernorTask governor = UniverseGovernorTask.instance();
        governor.run(); // update metrics

        double currentMspt = governor.getCurrentMspt();
        double currentTps = governor.getCurrentTps();
        double[] tpsArr = Bukkit.getTPS();

        double t1m = governor.getTps1m();
        double t5m = governor.getTps5m();
        double t15m = governor.getTps15m();
        double t30m = governor.getTps30m();
        double t1h = governor.getTps1h();
        double t3h = governor.getTps3h();
        double t6h = governor.getTps6h();
        double t12h = governor.getTps12h();
        double t1d = governor.getTps1d();
        NamedTextColor msptColor = (currentMspt <= 35.0D) ? NamedTextColor.GREEN : (currentMspt <= 45.0D) ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024L / 1024L;
        long maxMb = rt.maxMemory() / 1024L / 1024L;
        double memPercent = (maxMb > 0) ? ((double) usedMb / (double) maxMb) * 100.0D : 0.0D;
        NamedTextColor memColor = (memPercent < 75.0D) ? NamedTextColor.GREEN : (memPercent < 90.0D) ? NamedTextColor.YELLOW : NamedTextColor.RED;

        int chunks = org.purpurmc.purpur.web.UniverseWebPanel.instance().getCachedLoadedChunks();
        int players = Bukkit.getOnlinePlayers().size();

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Telemetría de Rendimiento en Tiempo Real", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        Component tpsShort = Component.text(" • TPS (1m, 5m, 15m, 30m): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", t1m), formatTpsColor(t1m)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t5m), formatTpsColor(t5m)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t15m), formatTpsColor(t15m)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t30m), formatTpsColor(t30m)));
        sender.sendMessage(tpsShort);

        Component tpsLong = Component.text(" • TPS (1h, 3h, 6h, 12h, 1d): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", t1h), formatTpsColor(t1h)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t3h), formatTpsColor(t3h)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t6h), formatTpsColor(t6h)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t12h), formatTpsColor(t12h)))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", t1d), formatTpsColor(t1d)));
        sender.sendMessage(tpsLong);

        Component msptLine = Component.text(" • Tick Duration (MSPT): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f ms", currentMspt), msptColor))
                .append(Component.text(" (Ideal: < 40.0 ms | Target: " + UniversePurpurConfig.targetMspt + " ms)", NamedTextColor.DARK_GRAY));
        sender.sendMessage(msptLine);

        Component motorLine = Component.text(" • Motor en Uso (Engine): ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.getEngineName(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" [" + UniversePurpurConfig.engine + "]", NamedTextColor.DARK_AQUA));
        sender.sendMessage(motorLine);

        Component tunerLine = Component.text(" • Tuner de Rendimiento: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.getTunerName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" [" + UniversePurpurConfig.profile + "]", NamedTextColor.YELLOW))
                .append(Component.text(" | Gobernador: " + governor.getThrottleLevelName(), (governor.getThrottleLevel() == 0) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(tunerLine);

        Component memLine = Component.text(" • Memoria Heap (RAM): ", NamedTextColor.GRAY)
                .append(Component.text(usedMb + " MB", memColor, TextDecoration.BOLD))
                .append(Component.text(" / " + maxMb + " MB ", NamedTextColor.GRAY))
                .append(Component.text(String.format("(%.1f%%)", memPercent), memColor));
        sender.sendMessage(memLine);

        Component worldLine = Component.text(" • Entorno: ", NamedTextColor.GRAY)
                .append(Component.text(players + " Jugadores Conectados", NamedTextColor.WHITE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(chunks + " Chunks Cargados (O(1))", NamedTextColor.WHITE));
        sender.sendMessage(worldLine);

        if (org.purpurmc.purpur.task.UniverseMemoryGovernor.instance().isHibernating()) {
            sender.sendMessage(Component.text(" • Estado de Reposo: ", NamedTextColor.GRAY)
                    .append(Component.text("HIBERNACIÓN PROFUNDA ACTIVA", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" (0 Jugadores: chunks liberados, RAM al mínimo)", NamedTextColor.DARK_GRAY)));
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        return true;
    }

    private static NamedTextColor formatTpsColor(double tps) {
        if (tps >= 19.5D) return NamedTextColor.GREEN;
        if (tps >= 17.0D) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }
}

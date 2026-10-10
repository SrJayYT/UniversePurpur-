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

        NamedTextColor c1m = formatTpsColor(tpsArr.length > 0 ? tpsArr[0] : currentTps);
        NamedTextColor c5m = formatTpsColor(tpsArr.length > 1 ? tpsArr[1] : currentTps);
        NamedTextColor c15m = formatTpsColor(tpsArr.length > 2 ? tpsArr[2] : currentTps);

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

        Component tpsLine = Component.text(" • TPS (1m, 5m, 15m): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", tpsArr.length > 0 ? tpsArr[0] : currentTps), c1m))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", tpsArr.length > 1 ? tpsArr[1] : currentTps), c5m))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", tpsArr.length > 2 ? tpsArr[2] : currentTps), c15m));
        sender.sendMessage(tpsLine);

        Component msptLine = Component.text(" • Tick Duration (MSPT): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f ms", currentMspt), msptColor))
                .append(Component.text(" (Ideal: < 40.0 ms | Target: " + UniversePurpurConfig.targetMspt + " ms)", NamedTextColor.DARK_GRAY));
        sender.sendMessage(msptLine);

        Component governorLine = Component.text(" • Dynamic Governor: ", NamedTextColor.GRAY)
                .append(Component.text(governor.getThrottleLevelName(), (governor.getThrottleLevel() == 0) ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.text(" | Perfil: " + UniversePurpurConfig.profile, NamedTextColor.AQUA));
        sender.sendMessage(governorLine);

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

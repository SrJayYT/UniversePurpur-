package org.purpurmc.purpur.command;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.multiversion.UniverseMultiVersionManager;
import org.purpurmc.purpur.network.AntiCrashShield;
import org.purpurmc.purpur.task.UniverseGovernorTask;
import org.purpurmc.purpur.task.UniverseMemoryGovernor;

/**
 * Main Administration & Telemetry Command for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Command: /universepurpur
 * Aliases: /up, /universe, /upurpur
 * Subcommands: status, stats, memory, profile, version, help
 *
 * Author: DevPapo (admin@scon.host)
 */
public class UniversePurpurCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "stats", "memory", "profile", "version", "license", "help");
    private static final List<String> PROFILES = Arrays.asList(
            "ultra-low-ram-cpu",
            "extreme-potato-cpu",
            "pvp-competitive",
            "15k-hyper-hub",
            "15k-mega-arena",
            "15k-scale-smp",
            "balanced"
    );

    public UniversePurpurCommand(String name) {
        super(name);
        this.description = "Comando principal y telemetría avanzada de UniversePurpur Titan Engine";
        this.usageMessage = "/universepurpur <status|stats|memory|profile|version|license|help>";
        this.setAliases(Arrays.asList("up", "universe", "upurpur"));
        this.setPermission("universepurpur.command");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> sendStatus(sender);
            case "stats" -> sendStats(sender);
            case "memory", "mem", "ram" -> handleMemory(sender, args);
            case "profile" -> handleProfile(sender, args);
            case "version", "ver", "about" -> sendVersion(sender);
            case "license", "licencia" -> handleLicense(sender, args);
            default -> {
                sender.sendMessage(Component.text("Subcomando desconocido: '", NamedTextColor.RED)
                        .append(Component.text(args[0], NamedTextColor.YELLOW))
                        .append(Component.text("'. Usa ", NamedTextColor.RED))
                        .append(Component.text("/universepurpur help", NamedTextColor.AQUA))
                        .append(Component.text(" para ver la lista.", NamedTextColor.RED)));
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Panel de Comandos del Motor Titan", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        addHelpLine(sender, "/universepurpur status", "Muestra el estado en vivo: TPS, MSPT, Chunks, RAM y Gobernador.");
        addHelpLine(sender, "/universepurpur stats", "Muestra métricas detalladas: GC, Shield Anti-Crash y paquetes.");
        addHelpLine(sender, "/universepurpur memory [trim]", "Monitorea la memoria RAM o ejecuta una poda proactiva de caché.");
        addHelpLine(sender, "/universepurpur profile [perfil]", "Consulta o cambia el preset de rendimiento en caliente.");
        addHelpLine(sender, "/universepurpur version", "Muestra la versión de UniversePurpur y autoría de DevPapo.");
        addHelpLine(sender, "/universepurpur license", "Muestra el estado de autenticación y licencia SCON.");
        addHelpLine(sender, "/tps", "Alias rápido para telemetría de TPS y duración de ticks.");

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void addHelpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(" • ", NamedTextColor.GRAY)
                .append(Component.text(cmd, NamedTextColor.GOLD, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Clic para sugerir comando", NamedTextColor.WHITE))))
                .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
    }

    private void sendStatus(CommandSender sender) {
        UniverseGovernorTask governor = UniverseGovernorTask.instance();
        UniverseMemoryGovernor memory = UniverseMemoryGovernor.instance();
        governor.run();

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
        boolean hibernating = memory.isHibernating();

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Status en Vivo del Servidor", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • TPS (1m, 5m, 15m): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", tpsArr.length > 0 ? tpsArr[0] : currentTps), c1m))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", tpsArr.length > 1 ? tpsArr[1] : currentTps), c5m))
                .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", tpsArr.length > 2 ? tpsArr[2] : currentTps), c15m)));

        sender.sendMessage(Component.text(" • Duración de Tick (MSPT): ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f ms", currentMspt), msptColor))
                .append(Component.text(" (Target: " + UniversePurpurConfig.targetMspt + " ms | " + governor.getThrottleLevelName() + ")", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text(" • Memoria Heap (RAM): ", NamedTextColor.GRAY)
                .append(Component.text(usedMb + " MB", memColor, TextDecoration.BOLD))
                .append(Component.text(" / " + maxMb + " MB ", NamedTextColor.GRAY))
                .append(Component.text(String.format("(%.1f%%)", memPercent), memColor)));

        sender.sendMessage(Component.text(" • Estado del Servidor: ", NamedTextColor.GRAY)
                .append(hibernating
                        ? Component.text("HIBERNACIÓN PROFUNDA ACTIVA", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text(" (0 Jugadores: chunks liberados, RAM mínima)", NamedTextColor.DARK_GRAY))
                        : Component.text("ACTIVO (" + players + " Jugadores online)", NamedTextColor.WHITE, TextDecoration.BOLD)));

        sender.sendMessage(Component.text(" • Chunks y Espacio: ", NamedTextColor.GRAY)
                .append(Component.text(chunks + " Chunks Cargados", NamedTextColor.WHITE))
                .append(Component.text(" | Particionado 2D: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(org.purpurmc.purpur.util.SpatialGrid.instance().getBucketCount() + " buckets", NamedTextColor.AQUA)));

        sender.sendMessage(Component.text(" • MultiVersión Engine: ", NamedTextColor.GRAY)
                .append(Component.text("1.21.x -> 1.26.x (Full Range)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(UniverseMultiVersionManager.isMultiVersionActive() ? " [ACTIVO]" : " [CARGADO]", NamedTextColor.GREEN)));

        int javaPort = Bukkit.getPort();
        if (javaPort <= 0) {
            String envServerPort = System.getenv("SERVER_PORT");
            if (envServerPort != null && !envServerPort.isEmpty()) {
                try {
                    javaPort = Integer.parseInt(envServerPort.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        if (javaPort <= 0) javaPort = 25565;

        sender.sendMessage(Component.text(" • Puertos Activos: ", NamedTextColor.GRAY)
                .append(Component.text("MC Java: " + javaPort, NamedTextColor.WHITE))
                .append(Component.text(" | Web Admin: " + UniversePurpurConfig.webPanelPort, NamedTextColor.AQUA))
                .append(Component.text(" | Bedrock UDP: " + UniversePurpurConfig.geyserPort, NamedTextColor.GREEN)));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void sendStats(CommandSender sender) {
        UniverseMemoryGovernor mem = UniverseMemoryGovernor.instance();
        AntiCrashShield shield = AntiCrashShield.instance();
        long uptimeMinutes = ManagementFactory.getRuntimeMXBean().getUptime() / 60000L;

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Estadísticas Avanzadas de Rendimiento", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Tiempo de Actividad (Uptime): ", NamedTextColor.GRAY)
                .append(Component.text(uptimeMinutes + " minutos", NamedTextColor.WHITE)));

        sender.sendMessage(Component.text(" • Garbage Collector Pausas: ", NamedTextColor.GRAY)
                .append(Component.text(mem.getTotalGcPauseTimeMs() + " ms totales", NamedTextColor.YELLOW))
                .append(Component.text(" | Podas de RAM: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(mem.getAutoTrimCount() + " ejecuciones", NamedTextColor.AQUA)));

        sender.sendMessage(Component.text(" • Blindaje Anti-Crash (Ataques Bloqueados): ", NamedTextColor.GRAY)
                .append(Component.text(shield.getTotalBlockedExploits() + " totales", NamedTextColor.GREEN, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("    - Movimiento Inválido: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(shield.getBlockedInvalidMovement(), NamedTextColor.WHITE))
                .append(Component.text(" | Payloads Sobredimensionados: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(shield.getBlockedOversizedPayloads(), NamedTextColor.WHITE))
                .append(Component.text(" | Slots NBT: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(shield.getBlockedInvalidSlots(), NamedTextColor.WHITE)));

        sender.sendMessage(Component.text(" • Cuentas Vinculadas a Discord: ", NamedTextColor.GRAY)
                .append(Component.text(org.purpurmc.purpur.web.UniverseDiscordLinker.instance().getAllLinks().size() + " jugadores", NamedTextColor.LIGHT_PURPLE)));

        sender.sendMessage(Component.text(" • Red Netty & Paquetes: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.maxPacketsPerSecond + " pkt/s límite", NamedTextColor.WHITE))
                .append(Component.text(" (Burst: " + UniversePurpurConfig.packetBurstLimit + " | Token Bucket Activo)", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void handleMemory(CommandSender sender, String[] args) {
        UniverseMemoryGovernor mem = UniverseMemoryGovernor.instance();

        if (args.length > 1 && args[1].equalsIgnoreCase("trim")) {
            sender.sendMessage(Component.text("Iniciando poda proactiva de memoria RAM y descarga de chunks inactivos...", NamedTextColor.YELLOW));
            int freed = mem.trimCaches();
            sender.sendMessage(Component.text("¡Poda completada exitosamente! Chunks liberados: ", NamedTextColor.GREEN)
                    .append(Component.text(freed, NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(". Memoria física liberada al sistema operativo.", NamedTextColor.GREEN)));
            return;
        }

        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory() / 1024L / 1024L;
        long total = rt.totalMemory() / 1024L / 1024L;
        long max = rt.maxMemory() / 1024L / 1024L;
        long used = total - free;

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Telemetría de Memoria RAM", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Heap Usado: ", NamedTextColor.GRAY)
                .append(Component.text(used + " MB", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" / " + max + " MB máximo asignado", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text(" • Heap Comprometido: ", NamedTextColor.GRAY)
                .append(Component.text(total + " MB", NamedTextColor.WHITE))
                .append(Component.text(" (Memoria actualmente reclamada al SO)", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text(" • Umbral Auto-Trim: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.memoryTrimThresholdPercent + "%", NamedTextColor.GOLD))
                .append(Component.text(" | Ahorro Agresivo: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(UniversePurpurConfig.aggressiveMemorySaver ? "ACTIVADO" : "DESACTIVADO", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text(" • Hibernación en Reposo: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.idleHibernationEnabled ? "HABILITADA (Timeout: " + UniversePurpurConfig.idleHibernationTimeoutSeconds + "s)" : "DESHABILITADA", NamedTextColor.AQUA)));

        sender.sendMessage(Component.text(" Tip: Ejecuta ", NamedTextColor.GRAY)
                .append(Component.text("/universepurpur memory trim", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/universepurpur memory trim"))
                        .hoverEvent(HoverEvent.showText(Component.text("Haz clic para podar la memoria ahora", NamedTextColor.WHITE))))
                .append(Component.text(" para forzar la liberación inmediata de RAM.", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String newProf = args[1].toLowerCase(Locale.ROOT);
            if (!PROFILES.contains(newProf)) {
                sender.sendMessage(Component.text("Perfil desconocido: '", NamedTextColor.RED)
                        .append(Component.text(newProf, NamedTextColor.YELLOW))
                        .append(Component.text("'. Perfiles válidos: " + String.join(", ", PROFILES), NamedTextColor.RED)));
                return;
            }

            UniversePurpurConfig.setProfile(newProf);
            sender.sendMessage(Component.text("¡Perfil de UniversePurpur cambiado a '", NamedTextColor.GREEN)
                    .append(Component.text(newProf, NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("' con éxito!", NamedTextColor.GREEN)));
            return;
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Perfiles de Rendimiento", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Perfil Activo: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.profile, NamedTextColor.GOLD, TextDecoration.BOLD)));

        sender.sendMessage(Component.text(" • Perfiles Disponibles:", NamedTextColor.GRAY));
        for (String p : PROFILES) {
            sender.sendMessage(Component.text("   - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(p, p.equalsIgnoreCase(UniversePurpurConfig.profile) ? NamedTextColor.GREEN : NamedTextColor.WHITE, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/universepurpur profile " + p))
                            .hoverEvent(HoverEvent.showText(Component.text("Clic para aplicar perfil " + p, NamedTextColor.WHITE)))));
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void sendVersion(CommandSender sender) {
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text(UniversePurpurConfig.getEngineName(), NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Software: ", NamedTextColor.GRAY)
                .append(Component.text("UniversePurpur Enterprise Edition", NamedTextColor.WHITE, TextDecoration.BOLD)));

        sender.sendMessage(Component.text(" • Versión del Núcleo: ", NamedTextColor.GRAY)
                .append(Component.text("1.0.2-TITAN-RELEASE", NamedTextColor.GREEN))
                .append(Component.text(" (MC: " + Bukkit.getMinecraftVersion() + " | Build: " + Bukkit.getVersion() + ")", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text(" • Autor & Desarrollador: ", NamedTextColor.GRAY)
                .append(Component.text("DevPapo ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("(admin@scon.host)", NamedTextColor.YELLOW)));

        sender.sendMessage(Component.text(" • Motor en Uso (Engine): ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.getEngineName(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" [" + UniversePurpurConfig.engine + "]", NamedTextColor.DARK_AQUA)));

        sender.sendMessage(Component.text(" • Tuner de Rendimiento: ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.getTunerName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" [" + UniversePurpurConfig.profile + "]", NamedTextColor.YELLOW)));

        sender.sendMessage(Component.text(" • MultiVersión Engine: ", NamedTextColor.GRAY)
                .append(Component.text("1.21.x -> 1.26.x (Full Range)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" [ACTIVO]", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text(" • Repositorio GitHub: ", NamedTextColor.GRAY)
                .append(Component.text("https://github.com/SrJayYT/UniversePurpur-", NamedTextColor.BLUE)
                        .clickEvent(ClickEvent.openUrl("https://github.com/SrJayYT/UniversePurpur-"))
                        .hoverEvent(HoverEvent.showText(Component.text("Haz clic para abrir el repositorio", NamedTextColor.WHITE)))));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    list.add(sub);
                }
            }
            return list;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            List<String> list = new ArrayList<>();
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String prof : PROFILES) {
                if (prof.startsWith(prefix)) {
                    list.add(prof);
                }
            }
            return list;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("memory")) {
            if ("trim".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                return Collections.singletonList("trim");
            }
        }
        return Collections.emptyList();
    }

    private void handleLicense(CommandSender sender, String[] args) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("check") || args[1].equalsIgnoreCase("verify") || args[1].equalsIgnoreCase("reload"))) {
            sender.sendMessage(Component.text("[SCON Licensing] Re-verificando licencia con https://licences.scon.host...", NamedTextColor.AQUA));
            Bukkit.getScheduler().runTaskAsynchronously(new org.purpurmc.purpur.util.MinecraftInternalPlugin(), () -> {
                try {
                    String key = org.purpurmc.purpur.license.UniverseLicenseManager.resolveLicenseKey();
                    boolean ok = org.purpurmc.purpur.license.UniverseLicenseManager.performVerification(key, false);
                    if (ok) {
                        sender.sendMessage(Component.text("✔ Licencia autorizada con éxito en SCON Licensing.", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("❌ Error de validación: " + org.purpurmc.purpur.license.UniverseLicenseManager.message, NamedTextColor.RED));
                    }
                } catch (Exception e) {
                    sender.sendMessage(Component.text("❌ Excepción al verificar: " + e.getMessage(), NamedTextColor.RED));
                }
            });
            return;
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  SCON", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(" LICENSING » ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
                .append(Component.text("Estado de Licencia UniversePurpur", NamedTextColor.WHITE, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        boolean valid = org.purpurmc.purpur.license.UniverseLicenseManager.isValid;
        String key = org.purpurmc.purpur.license.UniverseLicenseManager.resolvedKey;
        String masked = org.purpurmc.purpur.license.UniverseLicenseManager.maskKey(key);
        String st = org.purpurmc.purpur.license.UniverseLicenseManager.status;

        sender.sendMessage(Component.text(" • Estado        : ", NamedTextColor.GRAY)
                .append(Component.text(st, valid ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));
        sender.sendMessage(Component.text(" • Clave         : ", NamedTextColor.GRAY)
                .append(Component.text(masked, NamedTextColor.AQUA)));
        sender.sendMessage(Component.text(" • Producto      : ", NamedTextColor.GRAY)
                .append(Component.text(UniversePurpurConfig.licenseProductName, NamedTextColor.LIGHT_PURPLE)));
        sender.sendMessage(Component.text(" • Discord ID    : ", NamedTextColor.GRAY)
                .append(Component.text(org.purpurmc.purpur.license.UniverseLicenseManager.customerDiscordId, NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text(" • Tag Servidor  : ", NamedTextColor.GRAY)
                .append(Component.text(org.purpurmc.purpur.license.UniverseLicenseManager.licenseTag, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(" • Duración      : ", NamedTextColor.GRAY)
                .append(Component.text(org.purpurmc.purpur.license.UniverseLicenseManager.expiresAt, NamedTextColor.GREEN)));
        sender.sendMessage(Component.text(" • Panel Central : ", NamedTextColor.GRAY)
                .append(Component.text("https://licences.scon.host", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("https://licences.scon.host"))));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private static NamedTextColor formatTpsColor(double tps) {
        if (tps >= 19.5D) return NamedTextColor.GREEN;
        if (tps >= 18.0D) return NamedTextColor.YELLOW;
        if (tps >= 15.0D) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }
}

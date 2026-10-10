package org.purpurmc.purpur.command;

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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.task.UniverseGovernorTask;

/**
 * Enhanced Version & About Command for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Aliases: /version, /ver, /about
 * Author: DevPapo (admin@scon.host)
 */
public class UniverseVersionCommand extends Command {

    public UniverseVersionCommand(String name) {
        super(name);
        this.description = "Muestra la versión, motor y autor de UniversePurpur (+15,000 Players Titan Engine)";
        this.usageMessage = "/version [plugin]";
        this.setPermission("bukkit.command.version");
        this.setAliases(Arrays.asList("ver", "about"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sendUniverseBrand(sender);
            return true;
        }

        String target = String.join(" ", args).trim();
        Plugin plugin = Bukkit.getPluginManager().getPlugin(target);
        if (plugin == null) {
            String lower = target.toLowerCase(Locale.ROOT);
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                    plugin = p;
                    break;
                }
            }
        }

        if (plugin != null) {
            describePlugin(sender, plugin);
        } else {
            sender.sendMessage(Component.text("No se encontró ningún plugin llamado '", NamedTextColor.RED)
                    .append(Component.text(target, NamedTextColor.YELLOW))
                    .append(Component.text("'. Usa ", NamedTextColor.RED))
                    .append(Component.text("/plugins", NamedTextColor.AQUA))
                    .append(Component.text(" para ver la lista completa.", NamedTextColor.RED)));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    private void sendUniverseBrand(CommandSender sender) {
        String version = Bukkit.getVersion();
        String mcVer = Bukkit.getMinecraftVersion();
        String profile = UniversePurpurConfig.profile != null ? UniversePurpurConfig.profile : "ultra-low-ram-cpu";
        int webPort = UniversePurpurConfig.webPanelPort;
        boolean panelRunning = org.purpurmc.purpur.web.UniverseWebPanel.instance().isRunning();

        UniverseGovernorTask governor = UniverseGovernorTask.instance();
        String throttle = governor.getThrottleLevelName();

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Titan Engine (+15,000 Players)", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Software: ", NamedTextColor.GRAY)
                .append(Component.text("UniversePurpur Enterprise Edition", NamedTextColor.WHITE, TextDecoration.BOLD)));

        sender.sendMessage(Component.text(" • Versión del Núcleo: ", NamedTextColor.GRAY)
                .append(Component.text("1.0.0-RELEASE", NamedTextColor.GREEN))
                .append(Component.text(" (MC: " + mcVer + " | Build: " + version + ")", NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text(" • Autor & Desarrollador: ", NamedTextColor.GRAY)
                .append(Component.text("DevPapo ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("(admin@scon.host)", NamedTextColor.YELLOW)));

        sender.sendMessage(Component.text(" • Arquitectura: ", NamedTextColor.GRAY)
                .append(Component.text("Java 24 LTS | Generational ZGC | Netty Lock-Free", NamedTextColor.DARK_AQUA)));

        sender.sendMessage(Component.text(" • Perfil de Rendimiento: ", NamedTextColor.GRAY)
                .append(Component.text(profile, NamedTextColor.GOLD))
                .append(Component.text(" (Gobernador MSPT: " + throttle + ")", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text(" • MultiVersión Engine: ", NamedTextColor.GRAY)
                .append(Component.text("1.21.x -> 1.26.x (Full Range)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(org.purpurmc.purpur.multiversion.UniverseMultiVersionManager.isMultiVersionActive()
                        ? " [ACTIVO - Compatible con todos los clientes]"
                        : " [ACTIVO - Auto-Deploy en arranque]", NamedTextColor.GREEN)));

        sender.sendMessage(Component.text(" • Panel Web & Portal: ", NamedTextColor.GRAY)
                .append(Component.text(panelRunning ? "ONLINE (Puerto " + webPort + ")" : "OFFLINE", panelRunning ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" | Admin: /admin", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text(" • Repositorio GitHub: ", NamedTextColor.GRAY)
                .append(Component.text("https://github.com/SrJayYT/UniversePurpur-", NamedTextColor.BLUE)
                        .clickEvent(ClickEvent.openUrl("https://github.com/SrJayYT/UniversePurpur-"))
                        .hoverEvent(HoverEvent.showText(Component.text("Haz clic para abrir el repositorio", NamedTextColor.WHITE)))));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void describePlugin(CommandSender sender, Plugin plugin) {
        PluginDescriptionFile desc = plugin.getDescription();
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(" Plugin: ", NamedTextColor.GRAY)
                .append(Component.text(desc.getName(), NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" v" + desc.getVersion(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        if (desc.getDescription() != null && !desc.getDescription().isEmpty()) {
            sender.sendMessage(Component.text(" • Descripción: ", NamedTextColor.GRAY)
                    .append(Component.text(desc.getDescription(), NamedTextColor.WHITE)));
        }

        if (desc.getWebsite() != null && !desc.getWebsite().isEmpty()) {
            sender.sendMessage(Component.text(" • Sitio Web: ", NamedTextColor.GRAY)
                    .append(Component.text(desc.getWebsite(), NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(desc.getWebsite()))));
        }

        if (!desc.getAuthors().isEmpty()) {
            sender.sendMessage(Component.text(" • Autores: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", desc.getAuthors()), NamedTextColor.GOLD)));
        }

        sender.sendMessage(Component.text(" • Estado: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.isEnabled() ? "Activo (Enabled)" : "Desactivado (Disabled)", plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }
}

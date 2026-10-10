package org.purpurmc.purpur.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Enhanced Plugins Command for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Aliases: /plugins, /pl
 * Author: DevPapo (admin@scon.host)
 */
public class UniversePluginsCommand extends Command {

    public UniversePluginsCommand(String name) {
        super(name);
        this.description = "Muestra la lista de plugins instalados en UniversePurpur";
        this.usageMessage = "/plugins";
        this.setPermission("bukkit.command.plugins");
        this.setAliases(Arrays.asList("pl"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Plugins del Servidor (" + plugins.length + ")", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        if (plugins.length == 0) {
            sender.sendMessage(Component.text(" No hay plugins externos cargados.", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" (Nota: Todas las funciones de UniversePurpur: Panel Web, Gobernador MSPT,", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Anti-Crash, Grids 2D y Vinculación Discord están integradas en el núcleo).", NamedTextColor.GRAY));
        } else {
            for (Plugin p : plugins) {
                boolean enabled = p.isEnabled();
                String ver = p.getDescription().getVersion();
                String authors = !p.getDescription().getAuthors().isEmpty()
                        ? " por " + String.join(", ", p.getDescription().getAuthors())
                        : "";

                Component item = Component.text(" • ", NamedTextColor.GRAY)
                        .append(Component.text(p.getName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(" (v" + ver + ")", NamedTextColor.YELLOW))
                        .append(Component.text(authors, NamedTextColor.DARK_GRAY))
                        .hoverEvent(HoverEvent.showText(Component.text("Clic para ver detalles de " + p.getName(), NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.runCommand("/version " + p.getName()));

                sender.sendMessage(item);
            }
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }
}

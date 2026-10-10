package org.purpurmc.purpur.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Enhanced Help Command for UniversePurpur (+15,000 Players Titan Engine).
 *
 * Aliases: /help, /?
 * Author: DevPapo (admin@scon.host)
 */
public class UniverseHelpCommand extends Command {

    public UniverseHelpCommand(String name) {
        super(name);
        this.description = "Muestra el menú de ayuda y comandos de UniversePurpur";
        this.usageMessage = "/help [1|2]";
        this.setPermission("bukkit.command.help");
        this.setAliases(Arrays.asList("?"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Guía de Comandos (Página " + (page == 2 ? "2/2" : "1/2") + ")", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        if (page == 2) {
            sender.sendMessage(Component.text(" • Comandos de Servidor y Administración General:", NamedTextColor.GOLD, TextDecoration.BOLD));
            addCmd(sender, "/stop", "Detiene el servidor y guarda todos los mundos de forma segura.");
            addCmd(sender, "/reload", "Recarga configuraciones y permisos.");
            addCmd(sender, "/tps", "Muestra TPS en vivo (1m, 5m, 15m), MSPT y memoria RAM.");
            addCmd(sender, "/whitelist <on|off|add|remove>", "Controla la lista blanca de acceso al servidor.");
            addCmd(sender, "/kick <jugador> [motivo]", "Expulsa a un jugador inmediatamente.");
            addCmd(sender, "/ban <jugador> [motivo]", "Suspende a un jugador de forma permanente.");
            addCmd(sender, "/pardon <jugador>", "Levanta la sanción de un jugador suspendido.");
            sender.sendMessage(Component.text(" Usa /help 1 para volver a los comandos de Universe Titan.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(" • Comandos Principales de Universe Titan (+15k Engine):", NamedTextColor.GOLD, TextDecoration.BOLD));
            addCmd(sender, "/universespigot status", "Dashboard en vivo con TPS, MSPT, RAM, gobernador y chunks.");
            addCmd(sender, "/universespigot profile <perfil>", "Cambia preset en caliente (ultra-low-ram-cpu, 15k-hyper-hub).");
            addCmd(sender, "/universespigot memory [trim]", "Telemetría de Garbage Collection y poda proactiva de RAM.");
            addCmd(sender, "/universespigot purge <items|mobs>", "Limpia entidades acumuladas en todos los mundos.");
            addCmd(sender, "/universespigot shield", "Muestra estadísticas de ataques y exploits bloqueados.");
            addCmd(sender, "/link", "Genera un código temporal para vincular tu cuenta con Discord.");
            addCmd(sender, "/version", "Muestra la versión del núcleo, motor y desarrollador (DevPapo).");
            addCmd(sender, "/plugins", "Lista interactiva de plugins con autores y versiones.");
            sender.sendMessage(Component.text(" Usa /help 2 para ver más comandos administrativos.", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        return true;
    }

    private void addCmd(CommandSender sender, String cmd, String desc) {
        Component line = Component.text(" • ", NamedTextColor.GRAY)
                .append(Component.text(cmd, NamedTextColor.GREEN, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Clic para ejecutar " + cmd, NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.suggestCommand(cmd)))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.WHITE));
        sender.sendMessage(line);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("1", "2");
        }
        return Collections.emptyList();
    }
}

package org.purpurmc.purpur.command;

import java.io.File;
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
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.plugin.UniversePlugMan;

/**
 * Native PlugMan Command for UniversePurpur Titan Engine (+15,000 Players).
 * 100% compatible with modern Paper plugins (paper-plugin.yml) and Bukkit plugins.
 *
 * Aliases: /plugman, /plugmanx, /upm, /titanplugman, /universeplugman
 * Author: DevPapo (admin@scon.host)
 */
public class UniversePlugManCommand extends Command {

    public UniversePlugManCommand(String name) {
        super(name);
        this.description = "Gestión nativa de plugins en caliente para Paper y Bukkit (UniversePurpur Titan Engine)";
        this.usageMessage = "/plugman <list|info|load|unload|reload|enable|disable|usage|help> [plugin]";
        this.setPermission("universepurpur.plugman");
        this.setAliases(Arrays.asList("plugmanx", "upm", "titanplugman", "universeplugman"));
    }

    @Override
    public boolean testPermission(CommandSender target) {
        if (target.isOp() || target.hasPermission("universepurpur.plugman") || target.hasPermission("bukkit.command.plugins")) {
            return true;
        }
        target.sendMessage(Component.text("No tienes permiso para ejecutar comandos de gestión de plugins.", NamedTextColor.RED));
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list", "ls" -> handleList(sender);
            case "info", "dump" -> handleInfo(sender, args);
            case "load" -> handleLoad(sender, args);
            case "unload" -> handleUnload(sender, args);
            case "reload", "restart" -> handleReload(sender, args);
            case "enable" -> handleEnable(sender, args);
            case "disable" -> handleDisable(sender, args);
            case "usage" -> handleUsage(sender, args);
            default -> {
                sender.sendMessage(Component.text("Subcomando desconocido: '" + args[0] + "'. Usa /plugman help.", NamedTextColor.RED));
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Titan PlugMan Engine (Paper Native)", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sendHelpRow(sender, "/plugman list", "Lista todos los plugins instalados con tag [Paper] / [Bukkit].");
        sendHelpRow(sender, "/plugman info <plugin>", "Muestra información detallada, arquitectura y dependencias.");
        sendHelpRow(sender, "/plugman reload <plugin>", "Recarga un plugin en caliente (100% soporte Paper & Bukkit).");
        sendHelpRow(sender, "/plugman load <archivo.jar>", "Carga dinámicamente un archivo JAR desde plugins/.");
        sendHelpRow(sender, "/plugman unload <plugin>", "Descarga un plugin y libera su ClassLoader de memoria.");
        sendHelpRow(sender, "/plugman enable <plugin>", "Habilita un plugin actualmente desactivado.");
        sendHelpRow(sender, "/plugman disable <plugin>", "Deshabilita un plugin sin descargar sus clases.");
        sendHelpRow(sender, "/plugman usage <plugin>", "Muestra los comandos registrados por el plugin.");

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void sendHelpRow(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(" • ", NamedTextColor.GRAY)
                .append(Component.text(cmd, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(Component.text("Clic para sugerir comando", NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.suggestCommand(cmd.split(" ")[0] + " ")));
    }

    private void handleList(CommandSender sender) {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  UNIVERSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("PURPUR ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text("Plugins Instalados (" + plugins.length + ")", NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        if (plugins.length == 0) {
            sender.sendMessage(Component.text(" No hay plugins cargados.", NamedTextColor.YELLOW));
            return;
        }

        int paperCount = 0;
        int bukkitCount = 0;

        for (Plugin p : plugins) {
            boolean enabled = p.isEnabled();
            boolean isPaper = UniversePlugMan.isPaperPlugin(p);
            if (isPaper) paperCount++; else bukkitCount++;

            Component tag = isPaper
                    ? Component.text(" [Paper]", NamedTextColor.AQUA, TextDecoration.BOLD)
                    : Component.text(" [Bukkit]", NamedTextColor.GOLD);

            Component item = Component.text(" • ", NamedTextColor.GRAY)
                    .append(Component.text(p.getName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
                    .append(tag)
                    .append(Component.text(" (v" + p.getDescription().getVersion() + ")", NamedTextColor.YELLOW))
                    .hoverEvent(HoverEvent.showText(Component.text("Clic para ver detalles de " + p.getName(), NamedTextColor.WHITE)))
                    .clickEvent(ClickEvent.runCommand("/plugman info " + p.getName()));

            sender.sendMessage(item);
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(" Total: " + plugins.length + " plugins (Paper: " + paperCount + " • Bukkit: " + bukkitCount + ")", NamedTextColor.GRAY));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman info <plugin>", NamedTextColor.RED));
            return;
        }
        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }

        boolean isPaper = UniversePlugMan.isPaperPlugin(p);

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("  Detalles del Plugin: ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(p.getName(), NamedTextColor.AQUA, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));

        sender.sendMessage(Component.text(" • Versión: ", NamedTextColor.GRAY).append(Component.text(p.getDescription().getVersion(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text(" • Arquitectura: ", NamedTextColor.GRAY).append(
                isPaper ? Component.text("Paper Plugin (Modern Lifecycle / paper-plugin.yml)", NamedTextColor.AQUA, TextDecoration.BOLD)
                        : Component.text("Spigot / Bukkit Plugin (plugin.yml)", NamedTextColor.GOLD, TextDecoration.BOLD)
        ));
        sender.sendMessage(Component.text(" • Estado: ", NamedTextColor.GRAY).append(
                p.isEnabled() ? Component.text("Habilitado (Activo)", NamedTextColor.GREEN) : Component.text("Deshabilitado (Inactivo)", NamedTextColor.RED)
        ));
        sender.sendMessage(Component.text(" • Clase Principal: ", NamedTextColor.GRAY).append(Component.text(p.getDescription().getMain(), NamedTextColor.WHITE)));

        if (!p.getDescription().getAuthors().isEmpty()) {
            sender.sendMessage(Component.text(" • Autores: ", NamedTextColor.GRAY).append(Component.text(String.join(", ", p.getDescription().getAuthors()), NamedTextColor.YELLOW)));
        }
        if (p.getDescription().getWebsite() != null && !p.getDescription().getWebsite().isEmpty()) {
            sender.sendMessage(Component.text(" • Sitio Web: ", NamedTextColor.GRAY).append(Component.text(p.getDescription().getWebsite(), NamedTextColor.AQUA)));
        }
        if (p.getDescription().getDescription() != null && !p.getDescription().getDescription().isEmpty()) {
            sender.sendMessage(Component.text(" • Descripción: ", NamedTextColor.GRAY).append(Component.text(p.getDescription().getDescription(), NamedTextColor.WHITE)));
        }
        if (!p.getDescription().getDepend().isEmpty()) {
            sender.sendMessage(Component.text(" • Dependencias: ", NamedTextColor.GRAY).append(Component.text(String.join(", ", p.getDescription().getDepend()), NamedTextColor.RED)));
        }
        if (!p.getDescription().getSoftDepend().isEmpty()) {
            sender.sendMessage(Component.text(" • Dependencias Opcionales: ", NamedTextColor.GRAY).append(Component.text(String.join(", ", p.getDescription().getSoftDepend()), NamedTextColor.LIGHT_PURPLE)));
        }

        List<String> cmds = UniversePlugMan.getPluginCommands(p);
        if (!cmds.isEmpty()) {
            sender.sendMessage(Component.text(" • Comandos (" + cmds.size() + "): ", NamedTextColor.GRAY).append(Component.text(String.join(", ", cmds), NamedTextColor.GREEN)));
        }

        sender.sendMessage(Component.text("-----------------------------------------------------", NamedTextColor.DARK_GRAY));
    }

    private void handleLoad(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman load <archivo.jar>", NamedTextColor.RED));
            return;
        }

        String target = args[1];
        if (!target.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            target += ".jar";
        }

        File serverRoot = UniversePurpurConfig.SERVER_ROOT_DIR != null ? UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
        File pluginsDir = new File(serverRoot, "plugins");
        File file = new File(pluginsDir, target);

        sender.sendMessage(Component.text("Intentando cargar plugin desde " + file.getName() + "...", NamedTextColor.GRAY));
        UniversePlugMan.PluginResult res = UniversePlugMan.load(file);
        if (res.success()) {
            sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(res.message())));
        } else {
            sender.sendMessage(Component.text("❌ ", NamedTextColor.RED).append(Component.text(res.message(), NamedTextColor.RED)));
        }
    }

    private void handleUnload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman unload <plugin>", NamedTextColor.RED));
            return;
        }

        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Descargando plugin " + p.getName() + "...", NamedTextColor.GRAY));
        UniversePlugMan.PluginResult res = UniversePlugMan.unload(p);
        if (res.success()) {
            sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(res.message())));
        } else {
            sender.sendMessage(Component.text("❌ ", NamedTextColor.RED).append(Component.text(res.message(), NamedTextColor.RED)));
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman reload <plugin>", NamedTextColor.RED));
            return;
        }

        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Recargando en caliente " + p.getName() + " (Soporte Paper & Bukkit)...", NamedTextColor.GRAY));
        UniversePlugMan.PluginResult res = UniversePlugMan.reload(p);
        if (res.success()) {
            sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(res.message())));
        } else {
            sender.sendMessage(Component.text("❌ ", NamedTextColor.RED).append(Component.text(res.message(), NamedTextColor.RED)));
        }
    }

    private void handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman enable <plugin>", NamedTextColor.RED));
            return;
        }
        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }
        UniversePlugMan.PluginResult res = UniversePlugMan.enable(p);
        if (res.success()) {
            sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(res.message())));
        } else {
            sender.sendMessage(Component.text("❌ ", NamedTextColor.RED).append(Component.text(res.message(), NamedTextColor.RED)));
        }
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman disable <plugin>", NamedTextColor.RED));
            return;
        }
        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }
        UniversePlugMan.PluginResult res = UniversePlugMan.disable(p);
        if (res.success()) {
            sender.sendMessage(Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(res.message())));
        } else {
            sender.sendMessage(Component.text("❌ ", NamedTextColor.RED).append(Component.text(res.message(), NamedTextColor.RED)));
        }
    }

    private void handleUsage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /plugman usage <plugin>", NamedTextColor.RED));
            return;
        }
        Plugin p = UniversePlugMan.findPlugin(args[1]);
        if (p == null) {
            sender.sendMessage(Component.text("Plugin no encontrado: " + args[1], NamedTextColor.RED));
            return;
        }

        List<String> cmds = UniversePlugMan.getPluginCommands(p);
        sender.sendMessage(Component.text("Comandos registrados por " + p.getName() + " (" + cmds.size() + "):", NamedTextColor.YELLOW));
        if (cmds.isEmpty()) {
            sender.sendMessage(Component.text(" • Ninguno", NamedTextColor.GRAY));
        } else {
            for (String c : cmds) {
                sender.sendMessage(Component.text(" • /" + c, NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("Clic para ejecutar /" + c, NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.runCommand("/" + c)));
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("list", "info", "reload", "restart", "load", "unload", "enable", "disable", "usage", "help");
            List<String> matched = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    matched.add(s);
                }
            }
            return matched;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("load")) {
                List<String> jars = new ArrayList<>();
                File serverRoot = UniversePurpurConfig.SERVER_ROOT_DIR != null ? UniversePurpurConfig.SERVER_ROOT_DIR : new File(".");
                File pluginsDir = new File(serverRoot, "plugins");
                if (pluginsDir.isDirectory()) {
                    File[] files = pluginsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                                jars.add(f.getName());
                            }
                        }
                    }
                }
                return jars;
            }

            if (Arrays.asList("info", "reload", "restart", "unload", "enable", "disable", "usage").contains(sub)) {
                List<String> pNames = new ArrayList<>();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        pNames.add(p.getName());
                    }
                }
                return pNames;
            }
        }

        return Collections.emptyList();
    }
}

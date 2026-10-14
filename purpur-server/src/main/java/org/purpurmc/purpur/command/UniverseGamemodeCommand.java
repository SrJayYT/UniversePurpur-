package org.purpurmc.purpur.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Comandos rápidos nativos de modo de juego para UniversePurpur Titan Engine:
 * /gmc, /gms, /gma, /gmsp, /gm (con soporte para especificar jugador).
 *
 * Autor: DevPapo (admin@scon.host)
 */
public class UniverseGamemodeCommand extends Command {

    private final @Nullable GameMode targetMode;
    private final @Nullable String modeName;

    public UniverseGamemodeCommand(@NotNull String name, @NotNull GameMode targetMode, @NotNull String modeName, @NotNull List<String> aliases) {
        super(name, "Cambiar modo de juego a " + modeName, "/" + name + " [jugador]", aliases);
        this.targetMode = targetMode;
        this.modeName = modeName;
        this.setPermission("universe.gamemode");
    }

    public UniverseGamemodeCommand(@NotNull String name, @NotNull List<String> aliases) {
        super(name, "Cambiar modo de juego", "/" + name + " <modo> [jugador]", aliases);
        this.targetMode = null;
        this.modeName = null;
        this.setPermission("universe.gamemode");
    }

    private static record ModeInfo(GameMode mode, String name) {}

    private static ModeInfo parseMode(String arg) {
        if (arg == null) return null;
        switch (arg.toLowerCase(Locale.ROOT)) {
            case "c":
            case "1":
            case "creative":
            case "creativo":
                return new ModeInfo(GameMode.CREATIVE, "Creativo");
            case "s":
            case "0":
            case "survival":
            case "supervivencia":
                return new ModeInfo(GameMode.SURVIVAL, "Supervivencia");
            case "a":
            case "2":
            case "adventure":
            case "aventura":
                return new ModeInfo(GameMode.ADVENTURE, "Aventura");
            case "sp":
            case "3":
            case "spectator":
            case "espectador":
                return new ModeInfo(GameMode.SPECTATOR, "Espectador");
            default:
                return null;
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("universe.gamemode") && !sender.hasPermission("minecraft.command.gamemode") && !sender.isOp()) {
            sender.sendMessage(Component.text("No tienes permisos para usar este comando.", NamedTextColor.RED));
            return true;
        }

        GameMode resolvedMode = this.targetMode;
        String resolvedName = this.modeName;
        int playerArgIndex = 0;

        if (resolvedMode == null) {
            if (args.length == 0) {
                sender.sendMessage(Component.text("Uso: /" + commandLabel + " <c|s|a|sp> [jugador]", NamedTextColor.RED));
                return true;
            }
            ModeInfo parsed = parseMode(args[0]);
            if (parsed == null) {
                sender.sendMessage(Component.text("Modo de juego desconocido '" + args[0] + "'. Usa c, s, a, sp.", NamedTextColor.RED));
                return true;
            }
            resolvedMode = parsed.mode();
            resolvedName = parsed.name();
            playerArgIndex = 1;
        }

        Player target = null;
        if (args.length > playerArgIndex) {
            target = Bukkit.getPlayerExact(args[playerArgIndex]);
            if (target == null) {
                sender.sendMessage(Component.text("Jugador '" + args[playerArgIndex] + "' no encontrado o desconectado.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Debes especificar un jugador desde la consola: /" + commandLabel + (this.targetMode == null ? " <modo>" : "") + " <jugador>", NamedTextColor.RED));
            return true;
        }

        target.setGameMode(resolvedMode);

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("Tu modo de juego ha cambiado a ", NamedTextColor.WHITE)
                    .append(Component.text("Modo " + resolvedName, NamedTextColor.GREEN, TextDecoration.BOLD)));
        } else {
            sender.sendMessage(Component.text("Modo de juego de " + target.getName() + " cambiado a ", NamedTextColor.WHITE)
                    .append(Component.text("Modo " + resolvedName, NamedTextColor.GREEN, TextDecoration.BOLD)));
            target.sendMessage(Component.text("Tu modo de juego ha cambiado a ", NamedTextColor.WHITE)
                    .append(Component.text("Modo " + resolvedName, NamedTextColor.GREEN, TextDecoration.BOLD)));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (this.targetMode == null) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                return List.of("creative", "survival", "adventure", "spectator", "c", "s", "a", "sp")
                        .stream().filter(m -> m.startsWith(prefix)).toList();
            } else if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}

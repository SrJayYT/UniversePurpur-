package org.purpurmc.purpur.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.web.UniverseDiscordLinker;

/**
 * In-Game Command to generate a 6-digit Discord verification code.
 *
 * Aliases: /link, /vincular, /discordlink
 * Author: DevPapo
 */
public class UniverseLinkCommand extends Command {

    public UniverseLinkCommand(String name) {
        super(name);
        this.description = "Vincula tu cuenta de Minecraft con Discord en la web pública";
        this.usageMessage = "/link";
        this.setAliases(Arrays.asList("vincular", "discordlink"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando solo puede ser ejecutado por jugadores en línea.", NamedTextColor.RED));
            return true;
        }

        UniverseDiscordLinker linker = UniverseDiscordLinker.instance();
        UniverseDiscordLinker.LinkedAccount existing = linker.getByPlayer(player.getUniqueId());
        if (existing != null) {
            player.sendMessage(Component.text("✔ Tu cuenta ya está vinculada con Discord: ", NamedTextColor.GREEN)
                    .append(Component.text(existing.discordTag, NamedTextColor.AQUA)));
            player.sendMessage(Component.text("Si deseas desvincularla, hazlo desde la web pública o con el Bot.", NamedTextColor.GRAY));
            return true;
        }

        String code = linker.generateCode(player);
        player.sendMessage(Component.text("========================================", NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text(" Vinculación de Cuenta con Discord ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("(UniversePurpur)", NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("========================================", NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Tu código de verificación único es: ", NamedTextColor.GRAY)
                .append(Component.text(code, NamedTextColor.AQUA, TextDecoration.BOLD)));
        player.sendMessage(Component.text("1. Entra a la web pública: ", NamedTextColor.GRAY)
                .append(Component.text("http://" + UniversePurpurConfig.webServerIpAddress + ":" + UniversePurpurConfig.webPanelPort, NamedTextColor.GOLD)));
        player.sendMessage(Component.text("2. Ingresa tu Nickname y este código para completar la vinculación.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("⏱ Este código expirará en 5 minutos.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("========================================", NamedTextColor.DARK_PURPLE));

        return true;
    }
}

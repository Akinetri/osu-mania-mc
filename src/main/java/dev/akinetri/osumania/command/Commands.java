package dev.akinetri.osumania.command;

import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.game.GameManager;
import dev.akinetri.osumania.game.InputKey;
import dev.akinetri.osumania.ui.MenuScreen;
import dev.akinetri.osumania.player.PlayerStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

public final class Commands {
    private Commands() {
    }

    public static void register(CommandManager commands, GameManager manager) {
        commands.register(playerCommand(new Command("menu", "songs"), (player, _) -> manager.openMenu(player)));
        commands.register(playerCommand(new Command("quit", "stop"), (player, _) -> manager.quit(player)));

        Command play = new Command("play");
        ArgumentInteger number = ArgumentType.Integer("номер");
        number.min(1);
        play.setDefaultExecutor((sender, _) -> sender.sendMessage(Component.text("Использование: /play <номер карты> (список: /menu)", NamedTextColor.GRAY)));
        play.addSyntax((sender, ctx) -> {
            if (!(sender instanceof Player player)) return;
            List<Beatmap> maps = manager.library().maps();
            int n = ctx.get(number);
            if (n > maps.size()) {
                player.sendMessage(Component.text("Карт всего " + maps.size(), NamedTextColor.RED));
                return;
            }
            manager.start(player, maps.get(n - 1));
        }, number);
        commands.register(play);

        Command speed = new Command("speed", "scroll");
        ArgumentInteger ms = ArgumentType.Integer("мс");
        ms.between(MenuScreen.MIN_SCROLL, MenuScreen.MAX_SCROLL);
        speed.setDefaultExecutor((sender, _) -> {
            if (sender instanceof Player player) {
                player.sendMessage(Component.text("Скорость: " + manager.profile(player).scrollMs
                        + " мс на всё поле. Изменить: /speed <" + MenuScreen.MIN_SCROLL + ".." + MenuScreen.MAX_SCROLL + ">", NamedTextColor.GRAY));
            }
        });
        speed.addSyntax((sender, ctx) -> {
            if (!(sender instanceof Player player)) return;
            PlayerStore.Profile profile = manager.profile(player);
            profile.scrollMs = ctx.get(ms);
            manager.saveProfiles();
            player.sendMessage(Component.text("Скорость: " + profile.scrollMs + " мс (со следующей игры)", NamedTextColor.GREEN));
        }, ms);
        commands.register(speed);

        Command offset = new Command("offset");
        ArgumentInteger offsetMs = ArgumentType.Integer("мс");
        offsetMs.between(MenuScreen.MIN_OFFSET, MenuScreen.MAX_OFFSET);
        offset.setDefaultExecutor((sender, _) -> {
            if (sender instanceof Player player) {
                player.sendMessage(Component.text("Оффсет: " + manager.profile(player).offsetMs
                        + " мс. Если звук запаздывает относительно нот — увеличь: /offset <мс>", NamedTextColor.GRAY));
            }
        });
        offset.addSyntax((sender, ctx) -> {
            if (!(sender instanceof Player player)) return;
            PlayerStore.Profile profile = manager.profile(player);
            profile.offsetMs = ctx.get(offsetMs);
            manager.saveProfiles();
            player.sendMessage(Component.text("Оффсет: " + profile.offsetMs + " мс (со следующей игры)", NamedTextColor.GREEN));
        }, offsetMs);
        commands.register(offset);

        commands.register(playerCommand(new Command("keys"), (player, _) -> {
            player.sendMessage(Component.text("Раскладка (переназначь клавиши в Настройки → Управление):", NamedTextColor.LIGHT_PURPLE));
            for (int k = 1; k <= Beatmap.MAX_KEYS; k++) {
                Component line = Component.text(k + "K: ", NamedTextColor.WHITE);
                InputKey[] layout = InputKey.layout(k);
                for (int i = 0; i < layout.length; i++) {
                    if (i > 0) line = line.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
                    line = line.append(Component.keybind(layout[i].keybind(), NamedTextColor.AQUA));
                }
                player.sendMessage(line);
            }
        }));
    }

    private static Command playerCommand(Command command, BiConsumer<Player, Object> action) {
        command.setDefaultExecutor((sender, ctx) -> {
            if (sender instanceof Player player) action.accept(player, ctx);
        });
        return command;
    }
}

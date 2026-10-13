package dev.akinetri.osumania.game;

import dev.akinetri.osumania.Config;
import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.beatmap.SongLibrary;
import dev.akinetri.osumania.pack.ResourcePack;
import dev.akinetri.osumania.player.PlayerStore;
import dev.akinetri.osumania.ui.Canvas;
import dev.akinetri.osumania.ui.MenuScreen;
import dev.akinetri.osumania.ui.ResultsScreen;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerResourcePackStatusEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameManager {
    private static final class Room {
        final InstanceContainer instance;
        @Nullable Entity camera;
        @Nullable MenuScreen menu;
        @Nullable ResultsScreen results;
        @Nullable GameSession session;

        Room(InstanceContainer instance) {
            this.instance = instance;
        }
    }

    private final Config config;
    private final SongLibrary library;
    private final ResourcePack pack;
    private final PlayerStore store;
    private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();

    public GameManager(Config config, SongLibrary library, ResourcePack pack, PlayerStore store) {
        this.config = config;
        this.library = library;
        this.pack = pack;
        this.store = store;
    }

    public SongLibrary library() {
        return library;
    }

    public PlayerStore.Profile profile(Player player) {
        return store.get(player.getUuid(), player.getUsername());
    }

    public void saveProfiles() {
        store.save();
    }

    public void register(GlobalEventHandler events) {
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            Room room = rooms.computeIfAbsent(player.getUuid(), _ -> new Room(createInstance()));
            event.setSpawningInstance(room.instance);
            player.setRespawnPoint(Canvas.CAMERA);
            if (event.isFirstConfig()) player.sendResourcePacks(pack.request(config.requirePack));
        });

        events.addListener(PlayerResourcePackStatusEvent.class, event -> {
            ResourcePackStatus status = event.getStatus();
            if (!config.requirePack) return;
            if (status == ResourcePackStatus.DECLINED || status == ResourcePackStatus.FAILED_DOWNLOAD
                    || status == ResourcePackStatus.INVALID_URL || status == ResourcePackStatus.FAILED_RELOAD) {
                event.getPlayer().kick(Component.text("Без ресурспака игра не работает: в нём музыка и всё оформление.\n", NamedTextColor.RED)
                        .append(Component.text("Статус: " + status.name(), NamedTextColor.GRAY)));
            }
        });

        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) setupPlayer(event.getPlayer());
        });

        events.addListener(PlayerInputEvent.class, event -> {
            Room room = rooms.get(event.getPlayer().getUuid());
            if (room == null) return;
            if (room.session != null) room.session.onInput();
            else if (room.results != null && room.results.isVisible()) room.results.onInput();
            else if (room.menu != null) room.menu.onInput();
        });

        events.addListener(InventoryPreClickEvent.class, event -> event.setCancelled(true));
        events.addListener(ItemDropEvent.class, event -> event.setCancelled(true));
        events.addListener(PlayerSwapItemEvent.class, event -> event.setCancelled(true));

        events.addListener(PlayerDisconnectEvent.class, event -> {
            Room room = rooms.remove(event.getPlayer().getUuid());
            if (room == null) return;
            if (room.session != null) room.session.quit();
            if (room.menu != null) room.menu.hide();
            if (room.results != null) room.results.hide();
            MinecraftServer.getSchedulerManager().scheduleNextTick(() ->
                    MinecraftServer.getInstanceManager().unregisterInstance(room.instance));
        });
    }

    private InstanceContainer createInstance() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setTime(18000);
        for (int x = -2; x <= 1; x++) {
            for (int z = -1; z <= 0; z++) instance.loadChunk(x, z).join();
        }
        return instance;
    }

    private void setupPlayer(Player player) {
        Room room = rooms.get(player.getUuid());
        if (room == null) return;
        player.setGameMode(GameMode.SPECTATOR);
        player.setFlyingSpeed(0);
        player.getInventory().clear();
        Entity camera = new Entity(EntityType.ITEM_DISPLAY);
        camera.setNoGravity(true);
        camera.setInstance(room.instance, Canvas.CAMERA);
        room.camera = camera;
        player.scheduler().buildTask(() -> lockCamera(player)).delay(TaskSchedule.millis(250)).schedule();

        room.menu = new MenuScreen(player, this, room.instance);
        room.results = new ResultsScreen(player, room.instance, () -> showMenu(player, null));

        player.sendMessage(Component.text("Добро пожаловать в osu!mania!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.sendMessage(Component.text("Ноты ловятся клавишами движения. Для 4K: влево, назад, вперёд, вправо — "
                + "удобно переназначить их на D F J K в Настройки → Управление. Раскладки для всех режимов: /keys", NamedTextColor.GRAY));
        player.scheduler().buildTask(() -> showMenu(player, null)).delay(TaskSchedule.millis(500)).schedule();
    }

    public void lockCamera(Player player) {
        Room room = rooms.get(player.getUuid());
        if (room == null || room.camera == null || !player.isOnline()) return;
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        Pos camera = Canvas.camera(profile(player).uiScale);
        room.camera.teleport(camera);
        player.teleport(camera);
        player.spectate(room.camera);
    }

    public enum Screen {NONE, MENU, PLAYING, RESULTS}

    public Screen screen(Player player) {
        Room room = rooms.get(player.getUuid());
        if (room == null) return Screen.NONE;
        if (room.session != null) return Screen.PLAYING;
        if (room.results != null && room.results.isVisible()) return Screen.RESULTS;
        if (room.menu != null && room.menu.isVisible()) return Screen.MENU;
        return Screen.NONE;
    }

    @Nullable GameSession session(Player player) {
        Room room = rooms.get(player.getUuid());
        return room == null ? null : room.session;
    }

    public boolean isPlaying(Player player) {
        Room room = rooms.get(player.getUuid());
        return room != null && room.session != null;
    }

    public void showMenu(Player player, @Nullable Beatmap select) {
        Room room = rooms.get(player.getUuid());
        if (room == null || room.menu == null || room.session != null) return;
        if (room.results != null) room.results.hide();
        lockCamera(player);
        room.menu.show(select);
    }

    public void openMenu(Player player) {
        if (isPlaying(player)) {
            player.sendMessage(Component.text("Сначала закончи игру или выйди: /quit", NamedTextColor.RED));
            return;
        }
        showMenu(player, null);
    }

    public void start(Player player, Beatmap map) {
        Room room = rooms.get(player.getUuid());
        if (room == null) return;
        if (room.session != null) {
            player.sendMessage(Component.text("Ты уже играешь. Выйти: /quit", NamedTextColor.RED));
            return;
        }
        if (room.menu != null) room.menu.hide();
        if (room.results != null) room.results.hide();
        lockCamera(player);
        room.session = new GameSession(player, room.instance, new Canvas(room.instance), map, profile(player), config,
                this::onFinish);
    }

    public void quit(Player player) {
        Room room = rooms.get(player.getUuid());
        if (room == null || room.session == null) {
            player.sendMessage(Component.text("Ты сейчас не играешь.", NamedTextColor.GRAY));
            return;
        }
        room.session.quit();
    }

    private void onFinish(GameSession session) {
        Player player = session.player();
        Room room = rooms.get(player.getUuid());
        if (room == null || room.session != session) return;
        room.session = null;
        if (!player.isOnline()) return;
        if (session.isQuit()) {
            showMenu(player, session.map());
            return;
        }
        ScoreState s = session.score();
        boolean record = store.submit(player.getUuid(), player.getUsername(), session.map(),
                new PlayerStore.Best(s.score(), s.accuracy(), s.maxCombo(), s.rank(), s.fullCombo()));
        if (room.results != null) room.results.show(session.map(), s, s.meanError(), record);
        if (Math.abs(s.meanError()) > 15) {
            player.sendMessage(Component.text(String.format("Подсказка: ты в среднем нажимаешь %s. Попробуй /offset %d",
                            s.meanError() > 0 ? "поздно" : "рано", profile(player).offsetMs + (int) Math.round(s.meanError())),
                    NamedTextColor.GRAY));
        }
    }

    public void stopAll() {
        for (Room room : rooms.values()) {
            if (room.session != null) room.session.quit();
        }
        store.save();
    }
}

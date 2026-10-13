package dev.akinetri.osumania.ui;

import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.game.GameManager;
import dev.akinetri.osumania.game.GameSession;
import dev.akinetri.osumania.game.InputKey;
import dev.akinetri.osumania.pack.ResourcePack;
import dev.akinetri.osumania.pack.Sprites;
import dev.akinetri.osumania.player.PlayerStore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MenuScreen {
    public static final int MIN_SCROLL = 250, MAX_SCROLL = 3000;
    public static final int MIN_OFFSET = -300, MAX_OFFSET = 300;

    static final TextColor PINK = TextColor.color(0xFF66AA);
    private static final TextColor SOFT = TextColor.color(0xB8B0C8);

    private static final double LIST_U = 5.6;
    private static final double CARD_W = 9.6, CARD_H = 1.2, CARD_STEP = 1.38;
    private static final int RADIUS = 4;
    private static final double INFO_U = -5.9;
    private static final double Z_BG = 14, Z_PANEL = 10.3, Z_ROW = 10.25, Z_CARD = 10.2, Z_TEXT = 10.0;
    private static final double[] SETTING_Y = {64.55, 64.0, 63.45, 62.9};

    private static final long REPEAT_DELAY_MS = 320, REPEAT_RATE_MS = 70;
    private static final long PREVIEW_DEBOUNCE_MS = 300, PREVIEW_LOOP_MS = 25_000;

    private static final Sound MOVE_SOUND = Sound.sound(Key.key("minecraft", "block.note_block.hat"), Sound.Source.MASTER, 0.35f, 1.8f);
    private static final Sound ADJUST_SOUND = Sound.sound(Key.key("minecraft", "block.note_block.pling"), Sound.Source.MASTER, 0.3f, 1.4f);
    private static final Sound PLAY_SOUND = Sound.sound(Key.key("minecraft", "entity.experience_orb.pickup"), Sound.Source.MASTER, 0.5f, 1.2f);

    private enum Setting {SPEED, OFFSET, HITSOUND, SCALE}

    private final class Card {
        final int index;
        final Entity sprite, title, sub;
        @Nullable Entity rank;
        double u, y;
        boolean selected;

        Card(int index, double u, double y, boolean selected) {
            this.index = index;
            this.u = u;
            this.y = y;
            this.selected = selected;
            Beatmap map = maps().get(index);
            sprite = canvas.sprite(selected ? Sprites.CARD_SELECTED : Sprites.CARD, u, y, Z_CARD, CARD_W, CARD_H);
            title = canvas.text(Component.text(cut(map.title(), 34), NamedTextColor.WHITE, TextDecoration.BOLD), u, y - 0.02, Z_TEXT, 1.3f);
            sub = canvas.text(Component.text(cut(map.artist(), 26), SOFT)
                            .append(Component.text("  [" + cut(map.version(), 20) + "]  ", NamedTextColor.WHITE))
                            .append(Component.text(map.keys() + "K", PINK)),
                    u, y - 0.46, Z_TEXT, 0.95f);
            PlayerStore.Best best = manager.profile(player).bests.get(PlayerStore.mapKey(map));
            if (best != null) rank = canvas.label(Sprites.rank(best.rank()), u + CARD_W / 2 - 0.55, y, Z_TEXT, 0.6);
        }

        void moveTo(double u, double y, boolean selected) {
            if (selected != this.selected) Canvas.setSprite(sprite, selected ? Sprites.CARD_SELECTED : Sprites.CARD);
            this.selected = selected;
            this.u = u;
            this.y = y;
            Canvas.moveTo(sprite, u, y, Z_CARD);
            Canvas.moveTo(title, u, y - 0.02, Z_TEXT);
            Canvas.moveTo(sub, u, y - 0.46, Z_TEXT);
            if (rank != null) Canvas.moveTo(rank, u + CARD_W / 2 - 0.55, y, Z_TEXT);
        }

        void remove() {
            canvas.remove(sprite);
            canvas.remove(title);
            canvas.remove(sub);
            canvas.remove(rank);
        }
    }

    private final Player player;
    private final GameManager manager;
    private final Instance instance;
    private final Canvas canvas;
    private final Map<Integer, Card> cards = new HashMap<>();
    private final boolean[] down = new boolean[InputKey.values().length];
    private final long[] repeatAt = new long[InputKey.values().length];

    private int selected;
    private int focus;
    private boolean visible;
    private @Nullable Task task;
    private @Nullable Entity background, title, artist, version, stats, notes, bestRank, bestText, rowHighlight;
    private final Entity[] settingTexts = new Entity[Setting.values().length];

    private @Nullable String playingPreview;
    private long previewSwitchAt = -1;
    private long previewStartedAt;

    public MenuScreen(Player player, GameManager manager, Instance instance) {
        this.player = player;
        this.manager = manager;
        this.instance = instance;
        this.canvas = new Canvas(instance);
    }

    private List<Beatmap> maps() {
        return manager.library().maps();
    }

    public boolean isVisible() {
        return visible;
    }

    public Beatmap selectedMap() {
        return maps().get(selected);
    }

    public synchronized void show(@Nullable Beatmap select) {
        if (visible) return;
        visible = true;
        if (select != null && maps().contains(select)) selected = maps().indexOf(select);
        selected = Math.clamp(selected, 0, maps().size() - 1);

        background = canvas.sprite(Sprites.DEFAULT_BG, 0, Canvas.EYE_Y, Z_BG, 44, 24.75, 9);
        canvas.label(Sprites.LOGO, INFO_U, 70.95, Z_TEXT, 0.85);
        canvas.sprite(Sprites.PANEL, INFO_U, 65.4, Z_PANEL, 9.8, 9.4);

        title = canvas.text(Component.empty(), INFO_U, 68.85, Z_TEXT, 2.0f);
        artist = canvas.text(Component.empty(), INFO_U, 68.3, Z_TEXT, 1.3f);
        version = canvas.text(Component.empty(), INFO_U, 67.75, Z_TEXT, 1.2f);
        stats = canvas.text(Component.empty(), INFO_U, 66.95, Z_TEXT, 1.15f);
        notes = canvas.text(Component.empty(), INFO_U, 66.45, Z_TEXT, 1.1f);
        bestText = canvas.text(Component.empty(), INFO_U + 0.7, 65.45, Z_TEXT, 1.2f);

        rowHighlight = canvas.sprite(Sprites.ROW, INFO_U, SETTING_Y[focus] + 0.16, Z_ROW, 8.8, 0.5);
        for (Setting s : Setting.values()) {
            settingTexts[s.ordinal()] = canvas.text(Component.empty(), INFO_U, SETTING_Y[s.ordinal()], Z_TEXT, 1.25f);
        }
        canvas.text(key(InputKey.SNEAK).append(text(" — следующая настройка   ", SOFT))
                        .append(key(InputKey.LEFT)).append(text(" / ", SOFT)).append(key(InputKey.RIGHT))
                        .append(text(" — изменить", SOFT)),
                INFO_U, 62.25, Z_TEXT, 0.95f);
        canvas.text(key(InputKey.JUMP).append(text(" — играть", NamedTextColor.WHITE, TextDecoration.BOLD)),
                INFO_U, 61.3, Z_TEXT, 1.5f);

        canvas.sprite(Sprites.HINT_BAR, 0, 60.05, Z_TEXT + 0.05, 26, 0.65);
        canvas.text(key(InputKey.FORWARD).append(text(" / ", SOFT)).append(key(InputKey.BACKWARD))
                        .append(text(" — выбор карты     ", SOFT))
                        .append(key(InputKey.JUMP)).append(text(" — играть     ", SOFT))
                        .append(key(InputKey.SPRINT)).append(text(" — случайная карта", SOFT)),
                0, 59.85, Z_TEXT, 1.0f);

        for (InputKey k : InputKey.values()) down[k.ordinal()] = k.isDown(player.inputs());
        layoutCards();
        refreshInfo();
        refreshSettings();
        previewSwitchAt = System.currentTimeMillis();
        task = instance.scheduler().buildTask(this::tick).repeat(TaskSchedule.nextTick()).schedule();
    }

    public synchronized void hide() {
        if (!visible) return;
        visible = false;
        if (task != null) task.cancel();
        task = null;
        stopPreview();
        cards.clear();
        canvas.clear();
    }

    private void layoutCards() {
        int n = maps().size();
        for (Iterator<Map.Entry<Integer, Card>> it = cards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Card> e = it.next();
            if (Math.abs(e.getKey() - selected) > RADIUS) {
                e.getValue().remove();
                it.remove();
            }
        }
        for (int i = Math.max(0, selected - RADIUS); i <= Math.min(n - 1, selected + RADIUS); i++) {
            int d = i - selected;
            double u = LIST_U + Math.abs(d) * 0.3 - (d == 0 ? 0.35 : 0);
            double y = Canvas.EYE_Y - d * CARD_STEP;
            Card card = cards.get(i);
            if (card == null) cards.put(i, new Card(i, u, y, d == 0));
            else card.moveTo(u, y, d == 0);
        }
    }

    private void refreshInfo() {
        Beatmap map = selectedMap();
        Canvas.setSprite(background, map.background() != null ? map.background() : Sprites.DEFAULT_BG);
        Canvas.setText(title, text(cut(map.title(), 28), NamedTextColor.WHITE, TextDecoration.BOLD));
        Canvas.setText(artist, text(cut(map.artist(), 40), SOFT));
        Canvas.setText(version, text("[" + cut(map.version(), 30) + "]", PINK)
                .append(text(map.creator().isBlank() ? "" : "  от " + cut(map.creator(), 20), SOFT)));
        int length = Math.max(0, map.lastTime() - map.firstTime()) / 1000;
        Canvas.setText(stats, text(map.keys() + "K", PINK, TextDecoration.BOLD)
                .append(text(String.format(Locale.ROOT, "   OD %s   %d BPM   %d:%02d",
                        trim(map.overallDifficulty()), Math.round(map.bpm()), length / 60, length % 60), NamedTextColor.WHITE)));
        String audio = map.soundEvent() == null && map.backing().isEmpty() ? "   (без аудио)" : "";
        Canvas.setText(notes, text("Нот " + map.notes().size() + "   длинных " + map.holdCount() + audio, SOFT));

        canvas.remove(bestRank);
        bestRank = null;
        PlayerStore.Best best = manager.profile(player).bests.get(PlayerStore.mapKey(map));
        if (best == null) {
            Canvas.setText(bestText, text("Рекорда пока нет", SOFT));
        } else {
            bestRank = canvas.label(Sprites.rank(best.rank()), INFO_U - 3.6, 65.6, Z_TEXT, 0.9);
            Canvas.setText(bestText, text("Рекорд ", SOFT)
                    .append(digits(GameSession.formatScore(best.score()), NamedTextColor.WHITE))
                    .append(text("   ", SOFT))
                    .append(digits(GameSession.formatAcc(best.accuracy()), TextColor.color(0x8FE3FF)))
                    .append(text("   ", SOFT))
                    .append(digits(best.maxCombo() + "x", best.fullCombo() ? TextColor.color(0xFFD24A) : NamedTextColor.WHITE)));
        }
    }

    private void refreshSettings() {
        PlayerStore.Profile p = manager.profile(player);
        String[] values = {
                "Скорость: " + p.scrollMs + " мс",
                "Оффсет: " + (p.offsetMs > 0 ? "+" : "") + p.offsetMs + " мс",
                "Звук нажатий: " + (p.hitSounds ? "вкл" : "выкл"),
                "Масштаб интерфейса: " + p.uiScale + "%",
        };
        for (Setting s : Setting.values()) {
            boolean focused = s.ordinal() == focus;
            Component line = focused
                    ? text("◀  ", PINK).append(text(values[s.ordinal()], NamedTextColor.WHITE, TextDecoration.BOLD)).append(text("  ▶", PINK))
                    : text(values[s.ordinal()], SOFT);
            Canvas.setText(settingTexts[s.ordinal()], line);
        }
        Canvas.moveTo(rowHighlight, INFO_U, SETTING_Y[focus] + 0.16, Z_ROW);
    }

    public synchronized void onInput() {
        if (!visible) return;
        long now = System.currentTimeMillis();
        for (InputKey k : InputKey.values()) {
            boolean isDown = k.isDown(player.inputs());
            if (isDown == down[k.ordinal()]) continue;
            down[k.ordinal()] = isDown;
            if (isDown) {
                repeatAt[k.ordinal()] = now + REPEAT_DELAY_MS;
                press(k);
                if (!visible) return;
            }
        }
    }

    private void press(InputKey key) {
        switch (key) {
            case FORWARD -> select(selected - 1);
            case BACKWARD -> select(selected + 1);
            case LEFT -> adjust(-1);
            case RIGHT -> adjust(1);
            case SNEAK -> {
                focus = (focus + 1) % Setting.values().length;
                refreshSettings();
                player.playSound(MOVE_SOUND);
            }
            case SPRINT -> {
                if (maps().size() > 1) {
                    int next;
                    do next = ThreadLocalRandom.current().nextInt(maps().size()); while (next == selected);
                    select(next);
                }
            }
            case JUMP -> play();
        }
    }

    private boolean repeats(InputKey key) {
        return key == InputKey.FORWARD || key == InputKey.BACKWARD || key == InputKey.LEFT || key == InputKey.RIGHT;
    }

    private void select(int index) {
        index = Math.clamp(index, 0, maps().size() - 1);
        if (index == selected) return;
        selected = index;
        layoutCards();
        refreshInfo();
        player.playSound(MOVE_SOUND);
        previewSwitchAt = System.currentTimeMillis() + PREVIEW_DEBOUNCE_MS;
    }

    private void adjust(int dir) {
        PlayerStore.Profile p = manager.profile(player);
        switch (Setting.values()[focus]) {
            case SPEED -> p.scrollMs = Math.clamp(p.scrollMs - dir * 50L, MIN_SCROLL, MAX_SCROLL);
            case OFFSET -> p.offsetMs = Math.clamp(p.offsetMs + dir * 5L, MIN_OFFSET, MAX_OFFSET);
            case HITSOUND -> p.hitSounds = !p.hitSounds;
            case SCALE -> {
                p.uiScale = Math.clamp(p.uiScale + dir * 10L, Canvas.MIN_SCALE, Canvas.MAX_SCALE);
                manager.lockCamera(player);
            }
        }
        manager.saveProfiles();
        refreshSettings();
        player.playSound(ADJUST_SOUND);
    }

    private void play() {
        Beatmap map = selectedMap();
        player.playSound(PLAY_SOUND);
        manager.start(player, map);
    }

    private synchronized void tick() {
        if (!visible) return;
        long now = System.currentTimeMillis();
        for (InputKey k : InputKey.values()) {
            if (down[k.ordinal()] && repeats(k) && now >= repeatAt[k.ordinal()]) {
                repeatAt[k.ordinal()] = now + REPEAT_RATE_MS;
                press(k);
            }
        }
        if (previewSwitchAt >= 0 && now >= previewSwitchAt) {
            previewSwitchAt = -1;
            String wanted = selectedMap().previewSound();
            if (wanted == null || !wanted.equals(playingPreview)) {
                stopPreview();
                startPreview(wanted, now);
            }
        } else if (playingPreview != null && now - previewStartedAt >= PREVIEW_LOOP_MS) {
            startPreview(playingPreview, now);
        }
    }

    private void startPreview(@Nullable String event, long now) {
        playingPreview = event;
        previewStartedAt = now;
        if (event != null) player.playSound(Sound.sound(Key.key(ResourcePack.NAMESPACE, event), Sound.Source.MASTER, 0.8f, 1f));
    }

    private void stopPreview() {
        if (playingPreview != null) player.stopSound(SoundStop.named(Key.key(ResourcePack.NAMESPACE, playingPreview)));
        playingPreview = null;
    }

    static Component text(String s, TextColor color, TextDecoration... decorations) {
        return Component.text(s, color, decorations);
    }

    static Component digits(String s, TextColor color) {
        return Component.text(s, color).font(Sprites.DIGITS_FONT);
    }

    static Component key(InputKey key) {
        return text("[", PINK).append(Component.keybind(key.keybind(), NamedTextColor.WHITE)).append(text("]", PINK));
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}

package dev.akinetri.osumania.ui;

import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.game.GameSession;
import dev.akinetri.osumania.game.InputKey;
import dev.akinetri.osumania.game.Judgment;
import dev.akinetri.osumania.game.ScoreState;
import dev.akinetri.osumania.pack.Sprites;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static dev.akinetri.osumania.ui.MenuScreen.PINK;
import static dev.akinetri.osumania.ui.MenuScreen.cut;
import static dev.akinetri.osumania.ui.MenuScreen.digits;
import static dev.akinetri.osumania.ui.MenuScreen.key;
import static dev.akinetri.osumania.ui.MenuScreen.text;

public final class ResultsScreen {
    private static final double Z_BG = 14, Z_PANEL = 10.3, Z_TEXT = 10.0;
    private static final TextColor SOFT = TextColor.color(0xB8B0C8);
    private static final long AUTO_CLOSE_MS = 20_000;

    private final Player player;
    private final Instance instance;
    private final Canvas canvas;
    private final Runnable onClose;
    private boolean visible;
    private boolean jumpDown;
    private long shownAt;
    private @Nullable Task task;

    public ResultsScreen(Player player, Instance instance, Runnable onClose) {
        this.player = player;
        this.instance = instance;
        this.canvas = new Canvas(instance);
        this.onClose = onClose;
    }

    public boolean isVisible() {
        return visible;
    }

    public synchronized void show(Beatmap map, ScoreState s, double meanError, boolean record) {
        if (visible) hide();
        visible = true;
        shownAt = System.currentTimeMillis();
        jumpDown = InputKey.JUMP.isDown(player.inputs());

        canvas.sprite(map.background() != null ? map.background() : Sprites.DEFAULT_BG, 0, Canvas.EYE_Y, Z_BG, 44, 24.75, 6);
        canvas.sprite(Sprites.PANEL, 0, 65.4, Z_PANEL, 20, 10.6);

        canvas.text(text(cut(map.artist() + " - " + map.title(), 48), NamedTextColor.WHITE, TextDecoration.BOLD), 0, 69.55, Z_TEXT, 1.8f);
        canvas.text(text("[" + cut(map.version(), 30) + "]", PINK)
                .append(text(map.creator().isBlank() ? "" : "  от " + cut(map.creator(), 20), SOFT)), 0, 69.0, Z_TEXT, 1.2f);

        double leftU = -5.2;
        if (record) canvas.label(Sprites.NEW_RECORD, leftU, 68.35, Z_TEXT, 0.45);
        canvas.label(Sprites.rank(s.rank()), leftU, 65.7, Z_TEXT, 3.9);
        canvas.text(digits(GameSession.formatScore(s.score()), NamedTextColor.WHITE), leftU, 62.75, Z_TEXT, 3.2f);
        canvas.text(digits(GameSession.formatAcc(s.accuracy()), TextColor.color(0x8FE3FF)), leftU, 62.0, Z_TEXT, 2.2f);
        if (s.fullCombo()) canvas.text(text("FULL COMBO", TextColor.color(0xFFD24A), TextDecoration.BOLD), leftU, 61.45, Z_TEXT, 1.3f);

        double labelU = 1.9, countU = 5.6;
        Judgment[] judgments = Judgment.values();
        for (int i = 0; i < judgments.length; i++) {
            double y = 68.0 - i * 0.62;
            canvas.label(Sprites.judgment(judgments[i].name()), labelU, y, Z_TEXT, 0.46);
            canvas.text(digits(Integer.toString(s.count(judgments[i])), NamedTextColor.WHITE), countU, y - 0.2, Z_TEXT, 2.0f);
        }
        canvas.text(text("Макс. комбо  ", SOFT).append(digits(s.maxCombo() + "x", NamedTextColor.WHITE)),
                3.7, 63.75, Z_TEXT, 1.3f);
        canvas.text(text("Средняя ошибка  ", SOFT)
                        .append(text(String.format(Locale.ROOT, "%+.1f мс", meanError), NamedTextColor.WHITE))
                        .append(text(meanError > 8 ? "  (поздно)" : meanError < -8 ? "  (рано)" : "", SOFT)),
                3.7, 63.2, Z_TEXT, 1.1f);

        canvas.text(key(InputKey.JUMP).append(text(" — продолжить", SOFT)), 0, 60.55, Z_TEXT, 1.2f);

        task = instance.scheduler().buildTask(() -> {
            if (System.currentTimeMillis() - shownAt >= AUTO_CLOSE_MS) close();
        }).repeat(TaskSchedule.seconds(1)).schedule();
    }

    public synchronized void onInput() {
        if (!visible) return;
        boolean isDown = InputKey.JUMP.isDown(player.inputs());
        boolean pressed = isDown && !jumpDown;
        jumpDown = isDown;
        if (pressed && System.currentTimeMillis() - shownAt > 600) close();
    }

    private synchronized void close() {
        if (!visible) return;
        hide();
        onClose.run();
    }

    public synchronized void hide() {
        if (!visible) return;
        visible = false;
        if (task != null) task.cancel();
        task = null;
        canvas.clear();
    }
}

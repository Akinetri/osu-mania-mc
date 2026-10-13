package dev.akinetri.osumania.game;

import dev.akinetri.osumania.Config;
import dev.akinetri.osumania.beatmap.Beatmap;
import dev.akinetri.osumania.beatmap.Beatmap.BackingSound;
import dev.akinetri.osumania.beatmap.Beatmap.HitObject;
import dev.akinetri.osumania.pack.ResourcePack;
import dev.akinetri.osumania.player.PlayerStore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import dev.akinetri.osumania.ui.Canvas;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class GameSession {
    private static final int FINISH_DELAY_MS = 1200;
    private static final Sound HIT_SOUND = Sound.sound(Key.key("minecraft", "block.note_block.hat"),
            Sound.Source.MASTER, 0.25f, 1.7f);

    private static final class NoteState {
        final HitObject note;
        Playfield.NoteVisual visual;
        Judgment head;
        boolean holding;

        NoteState(HitObject note) {
            this.note = note;
        }
    }

    private record PendingMotion(Playfield.NoteVisual visual, double at) {
    }

    private final Player player;
    private final Beatmap map;
    private final Playfield field;
    private final HitWindows windows;
    private final ScoreState score;
    private final InputKey[] layout;
    private final boolean[] down;
    private final List<NoteState> all;
    private final List<List<NoteState>> columns;
    private final int[] headIndex;
    private final NoteState[] holding;
    private final int offsetMs;
    private final int scrollMs;
    private final double inputCompensationMs;
    private final double tickMs = MinecraftServer.TICK_MS;
    private final boolean hitSounds;
    private final Consumer<GameSession> onFinish;

    private final long startNanos;
    private final int leadInMs;
    private final double spawnLeadMs;
    private int spawnIndex;
    private int backingIndex;
    private boolean audioStarted;
    private final ArrayDeque<PendingMotion> pendingMotions = new ArrayDeque<>();
    private final List<Playfield.NoteVisual> moving = new ArrayList<>();
    private long shownScore = -1;
    private double shownAcc = -1;
    private int shownCombo = -1;
    private boolean finished;
    private boolean quit;
    private double finishAt = -1;
    private final Task task;

    public GameSession(Player player, Instance instance, Canvas canvas, Beatmap map, PlayerStore.Profile profile, Config config,
                       Consumer<GameSession> onFinish) {
        this.player = player;
        this.map = map;
        this.onFinish = onFinish;
        this.offsetMs = profile.offsetMs;
        this.scrollMs = profile.scrollMs;
        this.hitSounds = profile.hitSounds && map.backing().isEmpty();
        this.layout = InputKey.layout(map.keys());
        this.down = new boolean[map.keys()];
        this.windows = new HitWindows(map.overallDifficulty(), config.hitWindowLeniencyMs);
        this.inputCompensationMs = config.clientTickCompensationMs + tickMs / 2;

        this.all = new ArrayList<>(map.notes().size());
        this.columns = new ArrayList<>();
        for (int i = 0; i < map.keys(); i++) columns.add(new ArrayList<>());
        for (HitObject note : map.notes()) {
            NoteState state = new NoteState(note);
            all.add(state);
            columns.get(note.column()).add(state);
        }
        this.headIndex = new int[map.keys()];
        this.holding = new NoteState[map.keys()];
        this.score = new ScoreState(map.notes().size() + (int) map.holdCount());

        Component title = Component.text(map.artist() + " - " + map.title(), NamedTextColor.WHITE)
                .append(Component.text(" [" + map.version() + "]", NamedTextColor.GRAY));
        this.field = new Playfield(canvas, map.keys(), scrollMs, layout, title, map.background());
        this.spawnLeadMs = field.spawnLeadMs();

        int needed = (int) Math.ceil(scrollMs + spawnLeadMs + 1500 + Math.max(0, -offsetMs)) - map.firstTime();
        this.leadInMs = Math.max(500, needed);
        this.startNanos = System.nanoTime();

        for (int i = 0; i < layout.length; i++) down[i] = layout[i].isDown(player.inputs());

        updateHud();
        this.task = instance.scheduler().buildTask(this::tick).repeat(TaskSchedule.nextTick()).schedule();
    }

    public Player player() {
        return player;
    }

    public Beatmap map() {
        return map;
    }

    public ScoreState score() {
        return score;
    }

    public boolean isQuit() {
        return quit;
    }

    public double currentChartTime() {
        return chartTime(System.nanoTime());
    }

    private double songTime(long nanos) {
        return (nanos - startNanos) / 1_000_000.0 - leadInMs;
    }

    private double chartTime(long nanos) {
        return songTime(nanos) - offsetMs;
    }

    private synchronized void tick() {
        if (finished) return;
        long nanos = System.nanoTime();
        double song = songTime(nanos);
        double chart = song - offsetMs;
        double soundHorizon = song + tickMs / 2;

        if (!audioStarted && soundHorizon >= 0) {
            audioStarted = true;
            if (map.soundEvent() != null) player.playSound(songSound());
            field.startProgress(map.lastTime() - song);
        }
        List<BackingSound> backing = map.backing();
        while (backingIndex < backing.size() && backing.get(backingIndex).time() <= soundHorizon) {
            BackingSound s = backing.get(backingIndex++);
            player.playSound(Sound.sound(Key.key(s.sound()), Sound.Source.MASTER, s.volume(), s.pitch()));
        }

        while (!pendingMotions.isEmpty() && pendingMotions.peek().at() <= chart) {
            PendingMotion pm = pendingMotions.poll();
            field.startMotion(pm.visual(), chart);
            moving.add(pm.visual());
        }

        judgeTimeouts(chart);

        while (spawnIndex < all.size() && all.get(spawnIndex).note.time() - scrollMs - spawnLeadMs <= chart) {
            NoteState state = all.get(spawnIndex++);
            double motionAt = chart + Playfield.MOTION_DELAY_MS;
            state.visual = field.spawn(state.note, motionAt);
            if (state.head == Judgment.MISS) field.markMissed(state.visual);
            pendingMotions.add(new PendingMotion(state.visual, motionAt));
        }

        for (Iterator<Playfield.NoteVisual> it = moving.iterator(); it.hasNext(); ) {
            Playfield.NoteVisual v = it.next();
            if (v.removeAt <= chart) {
                field.finish(v);
                it.remove();
            }
        }

        field.tick();
        updateHud();

        if (finishAt < 0 && allJudged()) finishAt = chart + FINISH_DELAY_MS;
        if (finishAt >= 0 && chart >= finishAt && !audioStillImportant(song)) finish();
    }

    private boolean audioStillImportant(double song) {
        return !map.backing().isEmpty() && backingIndex < map.backing().size() && song < map.lastTime() + 3000;
    }

    private boolean allJudged() {
        if (spawnIndex < all.size()) return false;
        for (int c = 0; c < columns.size(); c++) {
            if (headIndex[c] < columns.get(c).size() || holding[c] != null) return false;
        }
        return true;
    }

    private void judgeTimeouts(double chart) {
        for (int c = 0; c < columns.size(); c++) {
            List<NoteState> column = columns.get(c);
            while (headIndex[c] < column.size()) {
                NoteState s = column.get(headIndex[c]);
                if (chart <= s.note.time() + windows.lateLimit()) break;
                headIndex[c]++;
                s.head = Judgment.MISS;
                judge(Judgment.MISS);
                if (s.note.isHold()) judge(Judgment.MISS);
                if (s.visual != null) field.markMissed(s.visual);
            }
            NoteState h = holding[c];
            if (h != null && chart >= h.note.endTime()) {
                holding[c] = null;
                h.holding = false;
                judge(Judgment.MAX);
                field.burst(c);
                if (h.visual != null) {
                    field.finish(h.visual);
                }
            }
        }
    }

    public synchronized void onInput() {
        if (finished) return;
        double chart = chartTime(System.nanoTime()) - inputCompensationMs;
        for (int c = 0; c < layout.length; c++) {
            boolean now = layout[c].isDown(player.inputs());
            if (now == down[c]) continue;
            down[c] = now;
            field.setPressed(c, now);
            if (now) press(c, chart);
            else release(c, chart);
        }
    }

    private void press(int c, double time) {
        if (hitSounds) player.playSound(HIT_SOUND);
        if (holding[c] != null) return;
        List<NoteState> column = columns.get(c);
        if (headIndex[c] >= column.size()) return;
        NoteState s = column.get(headIndex[c]);
        double offset = time - s.note.time();
        Judgment j = windows.judgeHead(offset);
        if (j == null) return;
        headIndex[c]++;
        s.head = j;
        judge(j);
        if (j != Judgment.MISS) score.recordError(offset);

        if (j != Judgment.MISS) field.burst(c);
        if (!s.note.isHold()) {
            if (s.visual != null) field.finish(s.visual);
        } else if (j == Judgment.MISS) {
            judge(Judgment.MISS);
            if (s.visual != null) field.markMissed(s.visual);
        } else {
            s.holding = true;
            holding[c] = s;
            if (s.visual != null) field.hitHead(s.visual);
        }
    }

    private void release(int c, double time) {
        NoteState s = holding[c];
        if (s == null) return;
        holding[c] = null;
        s.holding = false;
        Judgment j = windows.judgeTail(time - s.note.endTime());
        judge(j);
        if (j != Judgment.MISS) field.burst(c);
        if (s.visual != null) {
            if (j == Judgment.MISS) field.markMissed(s.visual);
            else field.finish(s.visual);
        }
    }

    private void judge(Judgment j) {
        score.apply(j);
        field.showJudgment(j);
    }

    private void updateHud() {
        long sc = score.score();
        double acc = score.accuracy();
        if (sc != shownScore || acc != shownAcc) {
            shownScore = sc;
            shownAcc = acc;
            field.showScore(formatScore(sc), formatAcc(acc));
        }
        int combo = score.combo();
        if (combo != shownCombo) {
            shownCombo = combo;
            field.showCombo(combo);
        }
    }

    public static String formatScore(long score) {
        return String.format(Locale.ROOT, "%08d", score);
    }

    public static String formatAcc(double acc) {
        return String.format(Locale.ROOT, "%.2f%%", acc * 100);
    }

    private Sound songSound() {
        return Sound.sound(Key.key(ResourcePack.NAMESPACE, map.soundEvent()), Sound.Source.MASTER, 1f, 1f);
    }

    private void finish() {
        finished = true;
        cleanup();
        onFinish.accept(this);
    }

    public synchronized void quit() {
        if (finished) return;
        finished = true;
        quit = true;
        cleanup();
        if (player.isOnline()) {
            if (map.soundEvent() != null) player.stopSound(SoundStop.named(songSound().name()));
        }
        onFinish.accept(this);
    }

    private void cleanup() {
        task.cancel();
        for (NoteState s : all) {
            if (s.visual != null) field.finish(s.visual);
        }
        field.destroy();
    }
}

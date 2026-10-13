package dev.akinetri.osumania.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class Skin {
    public static final Color PINK = new Color(0xFF66AA);
    private static final Color DARK = new Color(0x120E1C);

    public static final Map<String, Color> LANES = Map.of(
            "outer", new Color(0xECEAF6),
            "inner", new Color(0x4CC3FF),
            "center", new Color(0xFFC83D));

    private final Map<String, BufferedImage> sprites = new LinkedHashMap<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();

    public Map<String, BufferedImage> sprites() {
        return sprites;
    }

    public Map<String, byte[]> files() {
        return files;
    }

    public static Skin generate() {
        Skin skin = new Skin();
        skin.stage();
        skin.notes();
        skin.judgments();
        skin.ui();
        skin.digitsFont();
        skin.hud();
        return skin;
    }

    private void stage() {
        BufferedImage lane = image(32, 128);
        Graphics2D g = graphics(lane);
        g.setPaint(new GradientPaint(0, 0, new Color(8, 6, 16, 150), 0, 128, new Color(8, 6, 16, 235)));
        g.fillRect(0, 0, 32, 128);
        g.setColor(new Color(255, 255, 255, 22));
        g.fillRect(0, 0, 1, 128);
        g.fillRect(31, 0, 1, 128);
        g.dispose();
        sprites.put(Sprites.STAGE_LANE, lane);

        BufferedImage border = image(8, 64);
        g = graphics(border);
        g.setPaint(new GradientPaint(0, 0, withAlpha(PINK, 0), 4, 0, withAlpha(Color.WHITE, 230), true));
        g.fillRect(0, 0, 8, 64);
        g.dispose();
        sprites.put(Sprites.STAGE_BORDER, border);

        BufferedImage line = image(256, 16);
        g = graphics(line);
        g.setPaint(new GradientPaint(0, 0, withAlpha(PINK, 0), 0, 8, withAlpha(PINK, 200), true));
        g.fillRect(0, 0, 256, 16);
        g.setColor(Color.WHITE);
        g.fillRect(0, 6, 256, 4);
        g.dispose();
        sprites.put(Sprites.STAGE_LINE, line);

        sprites.put(Sprites.STAGE_TOP, panelStrip(false));
        sprites.put(Sprites.STAGE_BOTTOM, panelStrip(true));

        BufferedImage barBg = image(64, 8);
        g = graphics(barBg);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect(0, 0, 64, 8, 8, 8);
        g.dispose();
        sprites.put(Sprites.BAR_BG, barBg);

        BufferedImage barFill = image(64, 8);
        g = graphics(barFill);
        g.setPaint(new GradientPaint(0, 0, PINK, 64, 0, new Color(0xFFB3D6)));
        g.fillRect(0, 0, 64, 8);
        g.dispose();
        sprites.put(Sprites.BAR_FILL, barFill);
    }

    private static BufferedImage panelStrip(boolean accentOnTop) {
        BufferedImage img = image(128, 64);
        Graphics2D g = graphics(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x221A33), 0, 64, new Color(0x0E0A16)));
        g.fillRect(0, 0, 128, 64);
        g.setColor(PINK);
        g.fillRect(0, accentOnTop ? 0 : 62, 128, 2);
        g.setColor(new Color(255, 255, 255, 18));
        for (int x = 0; x < 128; x += 8) g.fillRect(x, 0, 1, 64);
        g.dispose();
        return img;
    }

    private void notes() {
        for (Map.Entry<String, Color> lane : LANES.entrySet()) {
            String id = lane.getKey();
            Color c = lane.getValue();
            sprites.put(Sprites.head(id), noteHead(c));
            sprites.put(Sprites.body(id), holdBody(c));
            sprites.put(Sprites.key(id, false), key(c, false));
            sprites.put(Sprites.key(id, true), key(c, true));
            sprites.put(Sprites.light(id), keyLight(c));
            sprites.put(Sprites.hit(id), hitBurst(c));
        }
        Color gray = new Color(0x6A6670);
        sprites.put(Sprites.HEAD_MISS, noteHead(gray));
        sprites.put(Sprites.BODY_MISS, holdBody(gray));
    }

    private static BufferedImage noteHead(Color c) {
        BufferedImage img = image(128, 36);
        Graphics2D g = graphics(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(2, 2, 124, 32, 16, 16);
        g.setPaint(new LinearGradientPaint(0, 2, 0, 34, new float[]{0, 0.45f, 1},
                new Color[]{brighter(c, 0.55), c, darker(c, 0.45)}));
        g.fill(shape);
        g.setColor(new Color(255, 255, 255, 170));
        g.fillRoundRect(10, 6, 108, 5, 5, 5);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(darker(c, 0.7));
        g.draw(shape);
        g.dispose();
        return img;
    }

    private static BufferedImage holdBody(Color c) {
        BufferedImage img = image(64, 64);
        Graphics2D g = graphics(img);
        g.setPaint(new LinearGradientPaint(4, 0, 60, 0, new float[]{0, 0.5f, 1},
                new Color[]{withAlpha(brighter(c, 0.3), 230), withAlpha(c, 150), withAlpha(brighter(c, 0.3), 230)}));
        g.fillRect(4, 0, 56, 64);
        g.setColor(withAlpha(Color.WHITE, 200));
        g.fillRect(4, 0, 3, 64);
        g.fillRect(57, 0, 3, 64);
        g.dispose();
        return img;
    }

    private static BufferedImage key(Color c, boolean pressed) {
        BufferedImage img = image(64, 64);
        Graphics2D g = graphics(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(4, 4, 56, 56, 14, 14);
        if (pressed) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(32, 32), 40, new float[]{0, 1},
                    new Color[]{Color.WHITE, c}));
            g.fill(shape);
        } else {
            g.setPaint(new GradientPaint(0, 4, new Color(0x2A2238), 0, 60, new Color(0x16111F)));
            g.fill(shape);
        }
        g.setStroke(new BasicStroke(3f));
        g.setColor(pressed ? Color.WHITE : withAlpha(c, 200));
        g.draw(shape);
        g.setColor(pressed ? withAlpha(darker(c, 0.5), 200) : withAlpha(c, 160));
        g.fillOval(24, 24, 16, 16);
        g.dispose();
        return img;
    }

    private static BufferedImage keyLight(Color c) {
        BufferedImage img = image(32, 128);
        Graphics2D g = graphics(img);
        g.setPaint(new GradientPaint(0, 128, withAlpha(c, 170), 0, 0, withAlpha(c, 0)));
        g.fillRect(0, 0, 32, 128);
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new LinearGradientPaint(0, 0, 32, 0, new float[]{0, 0.3f, 0.7f, 1},
                new Color[]{withAlpha(Color.WHITE, 60), Color.WHITE, Color.WHITE, withAlpha(Color.WHITE, 60)}));
        g.fillRect(0, 0, 32, 128);
        g.dispose();
        return img;
    }

    private static BufferedImage hitBurst(Color c) {
        BufferedImage img = image(64, 64);
        Graphics2D g = graphics(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(32, 32), 32, new float[]{0, 0.25f, 0.6f, 1},
                new Color[]{withAlpha(Color.WHITE, 255), withAlpha(brighter(c, 0.4), 230), withAlpha(c, 110), withAlpha(c, 0)}));
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        return img;
    }

    private void judgments() {
        putText(Sprites.judgment("MAX"), "PERFECT", 6, h -> rainbow(h));
        putText(Sprites.judgment("GREAT"), "GREAT", 6, h -> vertical(h, new Color(0xFFF1A6), new Color(0xFFB800)));
        putText(Sprites.judgment("GOOD"), "GOOD", 6, h -> vertical(h, new Color(0xC9FFB8), new Color(0x2FC94A)));
        putText(Sprites.judgment("OK"), "OK", 6, h -> vertical(h, new Color(0xBFE0FF), new Color(0x3C7BFF)));
        putText(Sprites.judgment("MEH"), "MEH", 6, h -> vertical(h, new Color(0xE6E6E6), new Color(0x8C8C8C)));
        putText(Sprites.judgment("MISS"), "MISS", 6, h -> vertical(h, new Color(0xFFB0B0), new Color(0xE02020)));

        putText(Sprites.rank("SS"), "SS", 12, h -> vertical(h, new Color(0xFFFBE0), new Color(0xFFC21A)));
        putText(Sprites.rank("S"), "S", 12, h -> vertical(h, new Color(0xFFF3B0), new Color(0xFF9F1A)));
        putText(Sprites.rank("A"), "A", 12, h -> vertical(h, new Color(0xC9FFB8), new Color(0x22B33E)));
        putText(Sprites.rank("B"), "B", 12, h -> vertical(h, new Color(0xBFE6FF), new Color(0x2F7DFF)));
        putText(Sprites.rank("C"), "C", 12, h -> vertical(h, new Color(0xF0C8FF), new Color(0xA03CFF)));
        putText(Sprites.rank("D"), "D", 12, h -> vertical(h, new Color(0xFFC0C0), new Color(0xE02626)));

        putText(Sprites.LOGO, "OSU!MANIA", 8, h -> vertical(h, Color.WHITE, PINK));
        putText(Sprites.NEW_RECORD, "NEW RECORD!", 6, h -> vertical(h, new Color(0xFFF6C0), new Color(0xFF9A1A)));
    }

    private void putText(String id, String text, int px, java.util.function.Function<Integer, Paint> fill) {
        sprites.put(id, PixelFont.render(text, px, Math.max(2, px / 3), fill, new Color(20, 10, 30)));
    }

    private static Paint vertical(int h, Color top, Color bottom) {
        return new GradientPaint(0, 0, top, 0, h, bottom);
    }

    private static Paint rainbow(int h) {
        return new LinearGradientPaint(0, 0, h * 5, 0, new float[]{0, 0.25f, 0.5f, 0.75f, 1},
                new Color[]{new Color(0x7DF9FF), new Color(0x9DFFB0), new Color(0xFFF59D), new Color(0xFFB3E6), new Color(0x9FB8FF)},
                MultipleGradientPaint.CycleMethod.REFLECT);
    }

    private void ui() {
        BufferedImage panel = image(128, 128);
        Graphics2D g = graphics(panel);
        RoundRectangle2D shape = new RoundRectangle2D.Double(2, 2, 124, 124, 18, 18);
        g.setPaint(new GradientPaint(0, 0, new Color(30, 22, 46, 225), 0, 128, new Color(14, 10, 22, 235)));
        g.fill(shape);
        g.setStroke(new BasicStroke(2f));
        g.setColor(withAlpha(PINK, 200));
        g.draw(shape);
        g.dispose();
        sprites.put(Sprites.PANEL, panel);

        sprites.put(Sprites.CARD, card(false));
        sprites.put(Sprites.CARD_SELECTED, card(true));

        BufferedImage row = image(128, 16);
        g = graphics(row);
        g.setPaint(new GradientPaint(0, 0, withAlpha(PINK, 0), 64, 0, withAlpha(PINK, 150), true));
        g.fillRect(0, 0, 128, 16);
        g.dispose();
        sprites.put(Sprites.ROW, row);

        BufferedImage hint = image(256, 16);
        g = graphics(hint);
        g.setPaint(new GradientPaint(0, 0, withAlpha(DARK, 0), 128, 0, withAlpha(DARK, 220), true));
        g.fillRect(0, 0, 256, 16);
        g.dispose();
        sprites.put(Sprites.HINT_BAR, hint);

        sprites.put(Sprites.DEFAULT_BG, defaultBackground());
    }

    private static BufferedImage card(boolean selected) {
        BufferedImage img = image(256, 48);
        Graphics2D g = graphics(img);
        RoundRectangle2D shape = new RoundRectangle2D.Double(2, 2, 252, 44, 14, 14);
        if (selected) {
            g.setPaint(new GradientPaint(0, 0, new Color(0xFF5FA2), 256, 0, new Color(0x9B4DFF)));
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(44, 30, 70, 215), 256, 0, new Color(24, 18, 40, 215)));
        }
        g.fill(shape);
        g.setStroke(new BasicStroke(selected ? 3f : 1.5f));
        g.setColor(selected ? Color.WHITE : withAlpha(PINK, 150));
        g.draw(shape);
        g.dispose();
        return img;
    }

    private static BufferedImage defaultBackground() {
        int w = 480, h = 270;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x3A1F5C), w, h, new Color(0x120C24)));
        g.fillRect(0, 0, w, h);
        Random random = new Random(727);
        for (int i = 0; i < 70; i++) {
            int size = 20 + random.nextInt(70);
            int x = random.nextInt(w + 40) - 20;
            int y = random.nextInt(h + 40) - 20;
            Polygon tri = new Polygon(new int[]{x, x + size / 2, x - size / 2},
                    new int[]{y - size / 2, y + size / 2, y + size / 2}, 3);
            Color c = random.nextBoolean() ? PINK : new Color(0x8A5CFF);
            g.setColor(withAlpha(c, 18 + random.nextInt(40)));
            g.fill(tri);
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h / 2.0), w * 0.7f, new float[]{0.4f, 1},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 170)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private void digitsFont() {
        String chars = "0123456789.,:%x-+/";
        int px = 2, outline = 1;
        int cellW = 5 * px + outline * 2 + 2, cellH = PixelFont.HEIGHT * px + outline * 2 + 2;
        BufferedImage sheet = image(cellW * chars.length(), cellH);
        Graphics2D g = graphics(sheet);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int i = 0; i < chars.length(); i++) {
            boolean[][] glyph = PixelFont.glyph(chars.charAt(i));
            int ox = i * cellW + outline, oy = outline;
            g.setColor(new Color(16, 8, 24));
            for (int y = 0; y < PixelFont.HEIGHT; y++) {
                for (int x = 0; x < glyph[y].length; x++) {
                    if (glyph[y][x]) g.fillRect(ox + x * px - outline, oy + y * px - outline, px + outline * 2, px + outline * 2);
                }
            }
            for (int y = 0; y < PixelFont.HEIGHT; y++) {
                int shade = 255 - y * 9;
                g.setColor(new Color(shade, shade, shade));
                for (int x = 0; x < glyph[y].length; x++) {
                    if (glyph[y][x]) g.fillRect(ox + x * px, oy + y * px, px, px);
                }
            }
        }
        g.dispose();
        files.put("assets/" + ResourcePack.NAMESPACE + "/textures/font/digits.png", ResourcePack.png(sheet));

        JsonObject bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", ResourcePack.NAMESPACE + ":font/digits.png");
        bitmap.addProperty("height", 9);
        bitmap.addProperty("ascent", 8);
        JsonArray rows = new JsonArray();
        rows.add(chars);
        bitmap.add("chars", rows);
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        advances.addProperty(" ", 4);
        space.add("advances", advances);
        JsonArray providers = new JsonArray();
        providers.add(space);
        providers.add(bitmap);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/" + ResourcePack.NAMESPACE + "/font/digits.json", font.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void hud() {
        byte[] blank = ResourcePack.png(image(1, 1));
        String[] hidden = {
                "hotbar", "hotbar_selection", "hotbar_offhand_left", "hotbar_offhand_right",
                "hotbar_attack_indicator_background", "hotbar_attack_indicator_progress",
                "crosshair_attack_indicator_background", "crosshair_attack_indicator_progress", "crosshair_attack_indicator_full",
                "experience_bar_background", "experience_bar_progress",
                "food_empty", "food_full", "food_half", "food_empty_hunger", "food_full_hunger", "food_half_hunger",
                "armor_empty", "armor_full", "armor_half", "air", "air_bursting", "air_empty",
                "heart/container", "heart/full", "heart/half", "heart/container_blinking", "heart/full_blinking",
                "heart/half_blinking", "heart/vehicle_container", "heart/vehicle_full", "heart/vehicle_half",
        };
        for (String sprite : hidden) files.put("assets/minecraft/textures/gui/sprites/hud/" + sprite + ".png", blank);

        files.put("assets/minecraft/textures/gui/sprites/hud/crosshair.png", blank);
    }

    static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static Color brighter(Color c, double k) {
        return new Color(mix(c.getRed(), 255, k), mix(c.getGreen(), 255, k), mix(c.getBlue(), 255, k));
    }

    private static Color darker(Color c, double k) {
        return new Color(mix(c.getRed(), 0, k), mix(c.getGreen(), 0, k), mix(c.getBlue(), 0, k));
    }

    private static int mix(int a, int b, double k) {
        return (int) Math.round(a + (b - a) * k);
    }
}

package dev.akinetri.osumania.pack;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class PixelFont {
    public static final int HEIGHT = 7;

    private static final Map<Character, boolean[][]> GLYPHS = new HashMap<>();

    static {
        def('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        def('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        def('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        def('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        def('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        def('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        def('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('I', "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###");
        def('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        def('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        def('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        def('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        def('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        def('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        def('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        def('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        def('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        def('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        def('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        def('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        def('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        def('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        def('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        def('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        def('1', ".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###");
        def('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        def('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        def('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        def('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        def('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        def('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        def('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        def('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        def('!', "#", "#", "#", "#", "#", ".", "#");
        def('%', "##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##");
        def('.', ".", ".", ".", ".", ".", ".", "#");
        def(',', "..", "..", "..", "..", "..", ".#", "#.");
        def(':', ".", ".", "#", ".", ".", "#", ".");
        def('-', "....", "....", "....", "####", "....", "....", "....");
        def('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        def('/', "....#", "....#", "...#.", "..#..", ".#...", "#....", "#....");
        def('x', ".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#");
        def(' ', "...", "...", "...", "...", "...", "...", "...");
    }

    private PixelFont() {
    }

    private static void def(char c, String... rows) {
        boolean[][] g = new boolean[HEIGHT][rows[0].length()];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < rows[y].length(); x++) g[y][x] = rows[y].charAt(x) == '#';
        }
        GLYPHS.put(c, g);
    }

    public static boolean[][] glyph(char c) {
        boolean[][] g = GLYPHS.get(c);
        if (g == null) g = GLYPHS.get(Character.toUpperCase(c));
        return g != null ? g : GLYPHS.get(' ');
    }

    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) w++;
            w += glyph(text.charAt(i))[0].length;
        }
        return w;
    }

    public static BufferedImage render(String text, int px, int outlinePx, Function<Integer, Paint> fill, Color outline) {
        int pad = outlinePx + px / 2;
        int w = width(text) * px + pad * 2;
        int h = HEIGHT * px + pad * 2;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(outline);
        forEachPixel(text, (x, y) -> g.fillRect(pad + x * px - outlinePx, pad + y * px - outlinePx,
                px + outlinePx * 2, px + outlinePx * 2));
        g.setColor(new Color(0, 0, 0, 90));
        forEachPixel(text, (x, y) -> g.fillRect(pad + x * px, pad + y * px + Math.max(1, px / 3), px, px));
        g.translate(pad, pad);
        g.setPaint(fill.apply(HEIGHT * px));
        forEachPixel(text, (x, y) -> g.fillRect(x * px, y * px, px, px));
        g.dispose();
        return image;
    }

    private interface PixelConsumer {
        void accept(int x, int y);
    }

    private static void forEachPixel(String text, PixelConsumer consumer) {
        int cursor = 0;
        for (int i = 0; i < text.length(); i++) {
            boolean[][] g = glyph(text.charAt(i));
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < g[y].length; x++) {
                    if (g[y][x]) consumer.accept(cursor + x, y);
                }
            }
            cursor += g[0].length + 1;
        }
    }
}

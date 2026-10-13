package dev.akinetri.osumania.pack;

import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Sprites {
    public static final String STAGE_LANE = "stage/lane";
    public static final String STAGE_BORDER = "stage/border";
    public static final String STAGE_LINE = "stage/line";
    public static final String STAGE_TOP = "stage/top";
    public static final String STAGE_BOTTOM = "stage/bottom";
    public static final String HEAD_MISS = "note/miss";
    public static final String BODY_MISS = "body/miss";
    public static final String BAR_BG = "ui/bar_bg";
    public static final String BAR_FILL = "ui/bar_fill";

    public static final String PANEL = "ui/panel";
    public static final String CARD = "ui/card";
    public static final String CARD_SELECTED = "ui/card_selected";
    public static final String ROW = "ui/row";
    public static final String HINT_BAR = "ui/hint_bar";
    public static final String LOGO = "ui/logo";
    public static final String NEW_RECORD = "ui/new_record";
    public static final String DEFAULT_BG = "bg/default";

    public static final Key DIGITS_FONT = Key.key(ResourcePack.NAMESPACE, "digits");

    private static final Map<String, Double> ASPECTS = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> ITEMS = new ConcurrentHashMap<>();

    private Sprites() {
    }

    public static String head(String lane) {
        return "note/" + lane;
    }

    public static String body(String lane) {
        return "body/" + lane;
    }

    public static String key(String lane, boolean pressed) {
        return "key/" + lane + (pressed ? "_down" : "");
    }

    public static String light(String lane) {
        return "light/" + lane;
    }

    public static String hit(String lane) {
        return "hit/" + lane;
    }

    public static String judgment(String name) {
        return "judgment/" + name.toLowerCase(Locale.ROOT);
    }

    public static String rank(String rank) {
        return "rank/" + rank.toLowerCase(Locale.ROOT);
    }

    static void registerAspect(String id, int width, int height) {
        ASPECTS.put(id, width / (double) height);
    }

    public static double aspect(String id) {
        return ASPECTS.getOrDefault(id, 1.0);
    }

    public static ItemStack item(String id) {
        return ITEMS.computeIfAbsent(id, i -> ItemStack.builder(Material.PAPER)
                .set(DataComponents.ITEM_MODEL, ResourcePack.NAMESPACE + ":" + i)
                .build());
    }
}

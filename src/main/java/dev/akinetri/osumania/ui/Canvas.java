package dev.akinetri.osumania.ui;

import dev.akinetri.osumania.pack.Sprites;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Canvas {
    public static final double EYE_Y = 65.6;
    public static final double SCREEN_Z = 10;
    public static final int DEFAULT_SCALE = 140, MIN_SCALE = 80, MAX_SCALE = 200;
    public static final Pos CAMERA = camera(DEFAULT_SCALE);

    public static Pos camera(int scalePercent) {
        double distance = SCREEN_Z * 100.0 / Math.clamp(scalePercent, MIN_SCALE, MAX_SCALE);
        return new Pos(0, EYE_Y, SCREEN_Z - distance, 0, 0);
    }
    public static final int MOVE_TICKS = 3;

    private final Instance instance;
    private final List<Entity> entities = new ArrayList<>();

    public Canvas(Instance instance) {
        this.instance = instance;
    }

    public static Pos at(double u, double y, double z) {
        return new Pos(-u, y, z);
    }

    public Entity sprite(String id, double u, double y, double z, double w, double h) {
        return sprite(id, u, y, z, w, h, 15);
    }

    public Entity sprite(String id, double u, double y, double z, double w, double h, int brightness) {
        return sprite(id, u, y, z, w, h, brightness, 0);
    }

    public Entity sprite(String id, double u, double y, double z, double w, double h, int brightness, double ty) {
        Entity entity = new Entity(EntityType.ITEM_DISPLAY);
        entity.setNoGravity(true);
        entity.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(Sprites.item(id));
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);
            meta.setScale(new Vec(w, h, 1));
            meta.setTranslation(new Vec(0, ty, 0));
            meta.setBrightness(brightness, brightness);
            meta.setViewRange(4f);
            meta.setPosRotInterpolationDuration(MOVE_TICKS);
        });
        return spawn(entity, at(u, y, z));
    }

    public Entity label(String id, double u, double y, double z, double h) {
        return sprite(id, u, y, z, h * Sprites.aspect(id), h);
    }

    public Entity text(Component text, double u, double y, double z, float scale) {
        Entity entity = new Entity(EntityType.TEXT_DISPLAY);
        entity.setNoGravity(true);
        entity.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(text);
            meta.setBackgroundColor(0);
            meta.setShadow(true);
            meta.setLineWidth(1000);
            meta.setScale(new Vec(scale, scale, scale));
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
            meta.setBrightness(15, 15);
            meta.setViewRange(4f);
            meta.setPosRotInterpolationDuration(MOVE_TICKS);
        });
        return spawn(entity, at(u, y, z));
    }

    private Entity spawn(Entity entity, Pos pos) {
        entity.setInstance(instance, pos);
        entities.add(entity);
        return entity;
    }

    public static void setSprite(Entity entity, String id) {
        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(Sprites.item(id)));
    }

    public static void setText(Entity entity, Component text) {
        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(text));
    }

    public static void transform(Entity entity, double tu, double ty, double w, double h, int ticks) {
        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> {
            meta.setTransformationInterpolationStartDelta(0);
            meta.setTransformationInterpolationDuration(ticks);
            meta.setTranslation(new Vec(-tu, ty, 0));
            meta.setScale(new Vec(w, h, 1));
        });
    }

    public static void moveTo(Entity entity, double u, double y, double z) {
        entity.teleport(at(u, y, z));
    }

    public void remove(@Nullable Entity entity) {
        if (entity == null) return;
        entity.remove();
        entities.remove(entity);
    }

    public void clear() {
        entities.forEach(Entity::remove);
        entities.clear();
    }
}

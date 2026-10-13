package dev.akinetri.osumania.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import dev.akinetri.osumania.beatmap.SongLibrary;
import dev.akinetri.osumania.beatmap.SongLibrary.AudioTrack;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePack {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePack.class);
    public static final String NAMESPACE = "osumania";

    private final Path file;
    private final String sha1;
    private final UUID uuid;
    private HttpServer http;
    private String url;

    private ResourcePack(Path file, String sha1) {
        this.file = file;
        this.sha1 = sha1;
        this.uuid = UUID.nameUUIDFromBytes(("osumania-pack-" + sha1).getBytes(StandardCharsets.UTF_8));
    }

    public static ResourcePack build(SongLibrary library, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Skin skin = Skin.generate();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            put(zip, "pack.mcmeta", packMeta().getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/" + NAMESPACE + "/sounds.json", soundsJson(library.tracks()).getBytes(StandardCharsets.UTF_8));
            for (AudioTrack track : library.tracks()) {
                putFile(zip, "assets/" + NAMESPACE + "/sounds/song/" + track.id() + ".ogg", track.ogg());
                if (track.preview() != null) {
                    putFile(zip, "assets/" + NAMESPACE + "/sounds/preview/" + track.id() + ".ogg", track.preview());
                }
            }
            for (Map.Entry<String, BufferedImage> sprite : skin.sprites().entrySet()) {
                putSprite(zip, sprite.getKey(), sprite.getValue());
            }
            for (SongLibrary.Background bg : library.backgrounds()) {
                BufferedImage image = ImageIO.read(bg.png().toFile());
                if (image != null) putSprite(zip, bg.sprite(), image);
            }
            for (Map.Entry<String, byte[]> file : skin.files().entrySet()) put(zip, file.getKey(), file.getValue());
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String hash = sha1(target);
        LOGGER.info("Ресурспак собран: {} ({} КБ, sha1 {})", target, Files.size(target) / 1024, hash);
        return new ResourcePack(target, hash);
    }

    private static String packMeta() {
        int format = MinecraftServer.RESOURCE_PACK_VERSION.major();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "osu!mania-mc");
        pack.addProperty("min_format", format);
        pack.addProperty("max_format", format);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root.toString();
    }

    private static String soundsJson(List<AudioTrack> tracks) {
        JsonObject root = new JsonObject();
        for (AudioTrack track : tracks) {
            root.add(track.soundEvent(), soundEvent(NAMESPACE + ":song/" + track.id()));
            if (track.preview() != null) root.add(track.previewEvent(), soundEvent(NAMESPACE + ":preview/" + track.id()));
        }
        return root.toString();
    }

    private static JsonObject soundEvent(String file) {
        JsonObject sound = new JsonObject();
        sound.addProperty("name", file);
        sound.addProperty("stream", true);
        JsonArray sounds = new JsonArray();
        sounds.add(sound);
        JsonObject event = new JsonObject();
        event.add("sounds", sounds);
        return event;
    }

    private static void putSprite(ZipOutputStream zip, String id, BufferedImage image) throws IOException {
        Sprites.registerAspect(id, image.getWidth(), image.getHeight());
        String texture = NAMESPACE + ":item/" + id;
        put(zip, "assets/" + NAMESPACE + "/textures/item/" + id + ".png", png(image));

        JsonObject textures = new JsonObject();
        textures.addProperty("t", texture);
        textures.addProperty("particle", texture);
        JsonObject faces = new JsonObject();
        faces.add("north", face());
        faces.add("south", face());
        JsonObject element = new JsonObject();
        element.add("from", vec(0, 0, 8));
        element.add("to", vec(16, 16, 8));
        element.addProperty("shade", false);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        put(zip, "assets/" + NAMESPACE + "/models/item/" + id + ".json", model.toString().getBytes(StandardCharsets.UTF_8));
        putItemDefinition(zip, id);
    }

    private static void putItemDefinition(ZipOutputStream zip, String id) throws IOException {
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", NAMESPACE + ":item/" + id);
        JsonObject item = new JsonObject();
        item.add("model", inner);
        put(zip, "assets/" + NAMESPACE + "/items/" + id + ".json", item.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject face() {
        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(0);
        uv.add(0);
        uv.add(16);
        uv.add(16);
        face.add("uv", uv);
        face.addProperty("texture", "#t");
        return face;
    }

    private static JsonArray vec(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }

    static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void putFile(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(entry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        return entry;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(entry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (DigestInputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void serve(String bindHost, int port, String publicBase) throws IOException {
        String path = "/pack-" + sha1 + ".zip";
        http = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals(path)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, Files.size(file));
                if (!exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        Files.copy(file, out);
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("Ошибка отдачи ресурспака: {}", e.toString());
            }
        });
        http.start();
        url = publicBase + path;
        LOGGER.info("Ресурспак доступен по адресу {}", url);
    }

    public void stop() {
        if (http != null) http.stop(0);
    }

    public ResourcePackRequest request(boolean required) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(uuid, URI.create(url), sha1))
                .required(required)
                .replace(true)
                .prompt(Component.text("Ресурспак с музыкой для osu!mania", NamedTextColor.LIGHT_PURPLE))
                .build();
    }
}

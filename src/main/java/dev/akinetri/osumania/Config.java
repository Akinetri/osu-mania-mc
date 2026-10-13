package dev.akinetri.osumania;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String host = "0.0.0.0";
    public int port = 25565;
    public boolean onlineMode = false;

    public String packBindHost = "0.0.0.0";
    public int packPort = 25580;
    public String packPublicUrl = "";
    public boolean requirePack = true;

    public String ffmpegPath = "ffmpeg";

    public int defaultScrollMs = 700;
    public int hitWindowLeniencyMs = 15;
    public int clientTickCompensationMs = 25;

    public int serverTps = 50;

    public String packUrl() {
        String base = packPublicUrl == null || packPublicUrl.isBlank()
                ? "http://127.0.0.1:" + packPort
                : packPublicUrl.strip();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static Config load(Path file) throws IOException {
        Config config;
        if (Files.exists(file)) {
            config = GSON.fromJson(Files.readString(file), Config.class);
            if (config == null) config = new Config();
        } else {
            config = new Config();
        }
        Files.writeString(file, GSON.toJson(config));
        return config;
    }
}

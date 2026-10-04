package com.goofy.goofyaddons.config;

import com.goofy.goofyaddons.GoofyAddons;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GoofyConfig {
    public List<Book> books = new ArrayList<>();


    public GoofyConfig() {
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise", 0, 0));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 2, 5, "Ultimate Wise", 0, 0));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("goofyaddons.json");

    public static GoofyConfig INSTANCE;


    public boolean speedMode = false;
    public int speedModeDelay = 100;
    public int minActionDelay = 100;
    public int maxActionDelay = 500;
    public String firstPage = "ec";
    public String secondPage = "ec 2";
    public boolean discordWebhookEnabled = false;
    public String discordWebhookUrl = "";
    public int discordWebhookIntervalSeconds = 300;
    public int discordWebhookChartRangeSeconds = 86400;
    public boolean roundGraphs = true;
    public int minBreakIntervalMinutes = 60;
    public int maxBreakIntervalMinutes = 120;
    public int minBreakDurationMinutes = 5;
    public int maxBreakDurationMinutes = 15;
    public boolean sleepTimeEnabled = false;
    public String sleepStartTime = "22:00";
    public String sleepEndTime = "07:00";


    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                GoofyConfig parsed = null;
                if (!json.isBlank()) {
                    parsed = GSON.fromJson(json, GoofyConfig.class);
                }
                if (parsed == null) {
                    INSTANCE = new GoofyConfig();
                    save();
                } else {
                    INSTANCE = parsed;
                }
            } else {
                INSTANCE = new GoofyConfig();
                save();
            }
        } catch (Exception e) {
            GoofyAddons.LOGGER.error("Could not load GoofyAddons config from {}", CONFIG_PATH, e);
            INSTANCE = new GoofyConfig();
            save();
        }
    }

    public static boolean save() {
        try {
            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(INSTANCE)
            );
            return true;
        } catch (Exception e) {
            GoofyAddons.LOGGER.error("Could not save GoofyAddons config to {}", CONFIG_PATH, e);
            return false;
        }
    }


}

package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.GoofyAddons;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfitTracker {
    public static final ProfitTracker INSTANCE = new ProfitTracker();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY_POINTS = 1000;

    private final Path dataPath = FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-profit.json");
    private ProfitData data = new ProfitData();
    private boolean persistenceAvailable = true;

    private ProfitTracker() {
        load();
    }

    public synchronized void trackPurchase(Book book) {
        if (findPosition(book) == null) data.positions.add(new Position(bookKey(book)));
        addPoint(data.unrealizedHistory, unrealizedProfit(), System.currentTimeMillis());
        save();
    }

    public synchronized void updateSellOffer(Book book, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        Position position = findPosition(book);
        if (position == null) {
            position = new Position(bookKey(book));
            data.positions.add(position);
        }

        position.sellOfferAmount = amount;
        position.hasSellOffer = true;
        addPoint(data.unrealizedHistory, unrealizedProfit(), System.currentTimeMillis());
        save();
    }

    public synchronized void closeSellOffer(Book book) {
        Position position = findPosition(book);
        if (position == null) return;

        data.positions.remove(position);
        addPoint(data.unrealizedHistory, unrealizedProfit(), System.currentTimeMillis());
        save();
    }

    public synchronized void recordClaimedCoins(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;

        data.realizedProfit += amount;
        addPoint(data.realizedHistory, data.realizedProfit, System.currentTimeMillis());
        save();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(data.realizedProfit, unrealizedProfit(), data.positions.size(),
                List.copyOf(data.realizedHistory), List.copyOf(data.unrealizedHistory));
    }

    public synchronized void reset() {
        data = new ProfitData();
        long timestamp = System.currentTimeMillis();
        data.realizedHistory.add(new ProfitPoint(timestamp, 0));
        data.unrealizedHistory.add(new ProfitPoint(timestamp, 0));
        persistenceAvailable = true;
        if (!save()) {
            throw new IllegalStateException("Could not save profit history; check the Minecraft log");
        }
    }

    private double unrealizedProfit() {
        return data.positions.stream()
                .mapToDouble(position -> position.hasSellOffer ? position.sellOfferAmount : 0)
                .sum();
    }

    private Position findPosition(Book book) {
        String key = bookKey(book);
        return data.positions.stream().filter(position -> position.key.equals(key)).findFirst().orElse(null);
    }

    private static String bookKey(Book book) {
        return book.id() + ":" + book.level() + ":" + book.sellLevel();
    }

    private static void addPoint(List<ProfitPoint> history, double value, long timestamp) {
        history.add(new ProfitPoint(timestamp, value));
        if (history.size() > MAX_HISTORY_POINTS) {
            history.subList(0, history.size() - MAX_HISTORY_POINTS).clear();
        }
    }

    private void load() {
        if (!Files.exists(dataPath)) {
            data.realizedHistory.add(new ProfitPoint(System.currentTimeMillis(), 0));
            data.unrealizedHistory.add(new ProfitPoint(System.currentTimeMillis(), 0));
            return;
        }
        try {
            ProfitData loaded = GSON.fromJson(Files.readString(dataPath), ProfitData.class);
            if (loaded == null) {
                throw new IllegalStateException("Profit history file is empty");
            }
            if (loaded.positions == null) loaded.positions = new ArrayList<>();
            if (loaded.realizedHistory == null) loaded.realizedHistory = new ArrayList<>();
            if (loaded.unrealizedHistory == null) loaded.unrealizedHistory = new ArrayList<>();
            data = loaded;
            if (data.version < 1) {
                data.version = 1;
                data.realizedProfit = 0;
                data.realizedHistory.clear();
                data.unrealizedHistory.clear();
            }
            if (data.realizedHistory.isEmpty()) {
                data.realizedHistory.add(new ProfitPoint(System.currentTimeMillis(), data.realizedProfit));
            }
            if (data.unrealizedHistory.isEmpty()) {
                data.unrealizedHistory.add(new ProfitPoint(System.currentTimeMillis(), unrealizedProfit()));
            }
            save();
        } catch (Exception exception) {
            persistenceAvailable = false;
            GoofyAddons.LOGGER.error("Could not load profit history from {}", dataPath, exception);
        }
    }

    private boolean save() {
        if (!persistenceAvailable) return false;
        try {
            Files.createDirectories(dataPath.getParent());
            Files.writeString(dataPath, GSON.toJson(data));
            return true;
        } catch (Exception exception) {
            persistenceAvailable = false;
            GoofyAddons.LOGGER.error("Could not save profit history to {}", dataPath, exception);
            return false;
        }
    }

    public record ProfitPoint(long timestamp, double value) {
    }

    public record Snapshot(double realizedProfit, double unrealizedProfit, int openFlips,
                           List<ProfitPoint> realizedHistory, List<ProfitPoint> unrealizedHistory) {
    }

    private static final class ProfitData {
        private int version = 1;
        private double realizedProfit;
        private List<Position> positions = new ArrayList<>();
        private List<ProfitPoint> realizedHistory = new ArrayList<>();
        private List<ProfitPoint> unrealizedHistory = new ArrayList<>();
    }

    private static final class Position {
        private final String key;
        private double sellOfferAmount;
        private boolean hasSellOffer;

        private Position(String key) {
            this.key = key;
        }
    }
}

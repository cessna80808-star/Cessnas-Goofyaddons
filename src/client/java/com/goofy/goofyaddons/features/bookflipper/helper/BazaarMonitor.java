package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.utils.ChatUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public class BazaarMonitor {
    private boolean running = false;
    private final HttpClient client = HttpClient.newHttpClient();
    private final long duration = 20000;
    private long startMs;
    private long lastUpdated;
    private final List<BazaarMonitorItem> monitorItemList = new ArrayList<>();
    private final List<Consumer<BazaarMonitorItem>> hookList = new ArrayList<>();

    public void add(Book book, double price, boolean isSellOrder) {
        ChatUtils.debugMessage("[BazaarMonitor] Book was added " + book.name() + " " + price + "sellorder=" + isSellOrder);
        synchronized (monitorItemList) {
            monitorItemList.add(new BazaarMonitorItem(book, price, isSellOrder));
        }
    }

    public void finish(Book book, boolean isSellOffer) {
        ChatUtils.debugMessage("[BazaarMonitor] Removing book " + book.name());
        synchronized (monitorItemList) {
            monitorItemList.removeIf(bazaarMonitorItem -> bazaarMonitorItem.isSellOrder == isSellOffer && bazaarMonitorItem.book.equals(book));
        }
    }

    public void reset() {
        synchronized (monitorItemList) {
            monitorItemList.clear();
        }
    }

    public List<ActiveOrder> activeOrders() {
        synchronized (monitorItemList) {
            return monitorItemList.stream()
                    .map(item -> new ActiveOrder(item.book.name() + " " + item.book.getRomanLevel(
                                    item.isSellOrder ? item.book.sellLevel() : item.book.level()),
                            item.isSellOrder ? "Sell" : "Buy", item.price))
                    .toList();
        }
    }

    private boolean hasActiveOrders() {
        synchronized (monitorItemList) {
            return !monitorItemList.isEmpty();
        }
    }

    public void hook(Consumer<BazaarMonitorItem> hook) {
        hookList.add(hook);
    }


    public void onTick() {
        if (!running) return;
        if (!((System.currentTimeMillis() - startMs) >= duration)) return;
        if (!hasActiveOrders()) return;
        startMs = System.currentTimeMillis();
        refresh();

    }

    public void start() {
        if (running) return;
        running = true;
        startMs = System.currentTimeMillis();
    }

    public void stop() {
        running = false;
    }

    public void refresh() {


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body ->
                        JsonParser.parseString(body).getAsJsonObject()
                )
                .thenAccept(root -> {

                    long lastUpdated = root.get("lastUpdated").getAsLong();

                    if (lastUpdated == this.lastUpdated) return;

                    this.lastUpdated = lastUpdated;

                    JsonObject products = root.getAsJsonObject("products");

                    List<BazaarMonitorItem> items;
                    synchronized (monitorItemList) {
                        items = new ArrayList<>(monitorItemList);
                    }
                    items.forEach(bazaarMonitorItem -> outbidScanner(products, bazaarMonitorItem));

                    synchronized (monitorItemList) {
                        monitorItemList.removeIf(BazaarMonitorItem::getOutbid);
                    }
                });

    }

    private void outbidScanner(JsonObject products, BazaarMonitorItem bazaarMonitorItem) {
        if (!bazaarMonitorItem.shouldCheck()) return;
        JsonObject productID = products.getAsJsonObject(bazaarMonitorItem.isSellOrder ? bazaarMonitorItem.book.getLevel(bazaarMonitorItem.book.sellLevel()) : bazaarMonitorItem.book.getLevel(bazaarMonitorItem.book.level()));
        if (!bazaarMonitorItem.isSellOrder) {
            JsonObject entry = productID.getAsJsonArray("sell_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) {
                bazaarMonitorItem.setOutbid();
                handleOutbid(bazaarMonitorItem);
            }
        } else {
            JsonObject entry = productID.getAsJsonArray("buy_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) {
                bazaarMonitorItem.setOutbid();
                handleOutbid(bazaarMonitorItem);
            }
        }

    }

    private void handleOutbid(BazaarMonitorItem bazaarMonitorItem) {
        ChatUtils.debugMessage("[BazaarMonitor] outbidding book " + bazaarMonitorItem.book.name());
        hookList.getFirst().accept(bazaarMonitorItem);
    }



    public class BazaarMonitorItem {
        public final boolean isSellOrder;
        public final Book book;
        private final double price;
        private boolean isOutbid = false;
        private long time;

        public BazaarMonitorItem(Book book, double price, boolean isSellOrder) {
            this.book = book;
            this.price = price;
            this.isSellOrder = isSellOrder;
            time = System.currentTimeMillis();
        }

        private void setOutbid() {
            isOutbid = true;
        }

        private boolean getOutbid() {
            return isOutbid;
        }

        private boolean shouldCheck() {
            if (!((System.currentTimeMillis() - time) >= duration)) return false;
            time = System.currentTimeMillis();
            return true;
        }
    }

}

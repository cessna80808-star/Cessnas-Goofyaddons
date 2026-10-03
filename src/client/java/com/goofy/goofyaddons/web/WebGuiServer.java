package com.goofy.goofyaddons.web;

import com.goofy.goofyaddons.GoofyAddons;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.bookflipper.helper.ProfitTracker;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WebGuiServer {
    private static final int PORT = 8765;
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final Gson GSON = new Gson();

    private WebGuiServer() {
    }

    public static void start(Minecraft minecraft) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 0);
            ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "GoofyAddons-WebGui");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/", exchange -> handle(exchange, minecraft));
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop(0);
                executor.shutdownNow();
            }, "GoofyAddons-WebGui-Shutdown"));
            GoofyAddons.LOGGER.info("GoofyAddons browser GUI available at http://127.0.0.1:{}", PORT);
        } catch (IOException exception) {
            GoofyAddons.LOGGER.error("Could not start the GoofyAddons browser GUI on port {}", PORT, exception);
        }
    }

    private static void handle(HttpExchange exchange, Minecraft minecraft) throws IOException {
        try {
            if (!isAllowedRequest(exchange)) {
                send(exchange, 403, "text/plain; charset=utf-8", "Forbidden");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/".equals(path) && "GET".equals(method)) {
                byte[] page;
                try (var input = WebGuiServer.class.getResourceAsStream("/assets/goofyaddons/web/index.html")) {
                    if (input == null) {
                        send(exchange, 500, "text/plain; charset=utf-8", "Browser GUI page is missing");
                        return;
                    }
                    page = input.readAllBytes();
                }
                send(exchange, 200, "text/html; charset=utf-8", page);
                return;
            }

            if ("/api/status".equals(path) && "GET".equals(method)) {
                String status = onClientThread(minecraft, () -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("running", FeatureManager.INSTANCE.isMacroRunning());
                    result.addProperty("feature", FeatureManager.INSTANCE.currentFeatureName());
                    result.addProperty("state", FeatureManager.INSTANCE.currentFeatureStatus());
                    result.addProperty("activeTasks", FeatureManager.INSTANCE.currentFeatureTaskCount());
                    result.add("activeFailsafes", GSON.toJsonTree(FailsafeManager.INSTANCE.activeFailsafes()));
                    return GSON.toJson(result);
                });
                send(exchange, 200, "application/json; charset=utf-8", status);
                return;
            }

            if ("/api/orders".equals(path) && "GET".equals(method)) {
                String orders = onClientThread(minecraft, () -> GSON.toJson(FeatureManager.INSTANCE.activeOrders()));
                send(exchange, 200, "application/json; charset=utf-8", orders);
                return;
            }

            if ("/api/profit".equals(path) && "GET".equals(method)) {
                String profit = onClientThread(minecraft, () -> GSON.toJson(ProfitTracker.INSTANCE.snapshot()));
                send(exchange, 200, "application/json; charset=utf-8", profit);
                return;
            }

            if ("/api/profit/reset".equals(path)) {
                if (!"POST".equals(method)) {
                    sendMethodNotAllowed(exchange, "POST");
                    return;
                }
                onClientThread(minecraft, () -> {
                    ProfitTracker.INSTANCE.reset();
                    return null;
                });
                send(exchange, 200, "application/json; charset=utf-8", "{\"reset\":true}");
                return;
            }

            if ("/api/config".equals(path)) {
                if ("GET".equals(method)) {
                    String config = onClientThread(minecraft, () -> GSON.toJson(GoofyConfig.INSTANCE));
                    send(exchange, 200, "application/json; charset=utf-8", config);
                    return;
                }
                if ("POST".equals(method)) {
                    JsonObject submitted = readJsonObject(exchange);
                    String result = onClientThread(minecraft, () -> {
                        GoofyConfig config = GoofyConfig.INSTANCE;
                        boolean speedMode = requiredSpeedMode(submitted);
                        int speedDelay = requiredInteger(submitted, "speedModeDelay");
                        int minDelay = requiredInteger(submitted, "minActionDelay");
                        int maxDelay = requiredInteger(submitted, "maxActionDelay");
                        String firstPage = requiredText(submitted, "firstPage");
                        String secondPage = requiredText(submitted, "secondPage");
                        boolean discordWebhookEnabled = requiredBoolean(submitted, "discordWebhookEnabled");
                        String discordWebhookUrl = requiredDiscordWebhookUrl(submitted);
                        var books = requiredBooks(submitted);

                        if (speedDelay < 1 || minDelay < 51 || maxDelay <= minDelay) {
                            throw new IllegalArgumentException("Invalid delay values");
                        }
                        if (discordWebhookEnabled && discordWebhookUrl.isBlank()) {
                            throw new IllegalArgumentException("Enter a Discord webhook URL to enable updates");
                        }

                        config.speedMode = speedMode;
                        config.speedModeDelay = speedDelay;
                        config.minActionDelay = minDelay;
                        config.maxActionDelay = maxDelay;
                        config.firstPage = firstPage;
                        config.secondPage = secondPage;
                        config.discordWebhookEnabled = discordWebhookEnabled;
                        config.discordWebhookUrl = discordWebhookUrl;
                        config.books = books;
                        if (!GoofyConfig.save()) {
                            throw new IllegalStateException("Could not save settings; check the Minecraft log");
                        }
                        return "{\"saved\":true}";
                    });
                    send(exchange, 200, "application/json; charset=utf-8", result);
                    return;
                }
                sendMethodNotAllowed(exchange, "GET, POST");
                return;
            }

            if ("/api/action".equals(path) && "POST".equals(method)) {
                JsonObject submitted = readJsonObject(exchange);
                String action = requiredText(submitted, "action");
                if (!"start".equals(action) && !"stop".equals(action)) {
                    send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Unknown action\"}");
                    return;
                }
                onClientThread(minecraft, () -> {
                    if ("start".equals(action)) {
                        FeatureManager.INSTANCE.start("BazaarFlipper");
                    } else {
                        FeatureManager.INSTANCE.stop();
                    }
                    return null;
                });
                send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
                return;
            }

            if (path.startsWith("/api/")) {
                send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Not found\"}");
                return;
            }
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, "application/json; charset=utf-8",
                    GSON.toJson(new ErrorResponse(exception.getMessage())));
        } catch (Exception exception) {
            GoofyAddons.LOGGER.error("Browser GUI request failed", exception);
            send(exchange, 503, "application/json; charset=utf-8",
                    "{\"error\":\"Request failed; check the Minecraft log\"}");
        } finally {
            exchange.close();
        }
    }

    private static boolean isAllowedRequest(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (!("localhost:" + PORT).equalsIgnoreCase(host)
                && !("127.0.0.1:" + PORT).equals(host)) {
            return false;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String originHost = uri.getHost();
            return "http".equals(uri.getScheme()) && PORT == uri.getPort()
                    && ("localhost".equalsIgnoreCase(originHost)
                    || "127.0.0.1".equals(originHost)
                    || "::1".equals(originHost));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static JsonObject readJsonObject(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large");
        }
        try {
            var parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Expected a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Invalid JSON request");
        }
    }

    private static boolean requiredSpeedMode(JsonObject json) {
        if (!json.has("speedMode") || !json.get("speedMode").isJsonPrimitive()
                || !json.getAsJsonPrimitive("speedMode").isBoolean()) {
            throw new IllegalArgumentException("Missing or invalid speedMode");
        }
        return json.get("speedMode").getAsBoolean();
    }

    private static boolean requiredBoolean(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isBoolean()) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        return json.get(key).getAsBoolean();
    }

    private static String requiredDiscordWebhookUrl(JsonObject json) {
        if (!json.has("discordWebhookUrl") || !json.get("discordWebhookUrl").isJsonPrimitive()
                || !json.getAsJsonPrimitive("discordWebhookUrl").isString()) {
            throw new IllegalArgumentException("Missing or invalid Discord webhook URL");
        }
        String value = json.get("discordWebhookUrl").getAsString().trim();
        if (value.isEmpty()) return "";
        if (value.length() > 500) {
            throw new IllegalArgumentException("Discord webhook URL is too long");
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !("discord.com".equalsIgnoreCase(uri.getHost())
                    || "discordapp.com".equalsIgnoreCase(uri.getHost()))
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/api/webhooks/\\d+/[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Enter a valid HTTPS Discord webhook URL");
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Enter a valid")) {
                throw exception;
            }
            throw new IllegalArgumentException("Enter a valid HTTPS Discord webhook URL");
        }
        return value;
    }

    private static int requiredInteger(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        String value = json.get(key).getAsString();
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " is outside the supported range");
        }
    }

    private static String requiredText(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        String value = json.get(key).getAsString().trim();
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException(key + " must contain 1 to 128 characters");
        }
        return value;
    }

    private static java.util.List<Book> requiredBooks(JsonObject json) {
        if (!json.has("books") || !json.get("books").isJsonArray()) {
            throw new IllegalArgumentException("Books must be a list");
        }
        var entries = json.getAsJsonArray("books");
        if (entries.size() > 100) {
            throw new IllegalArgumentException("A maximum of 100 books can be configured");
        }
        java.util.List<Book> books = new java.util.ArrayList<>();
        java.util.Set<String> uniqueEntries = new java.util.HashSet<>();
        for (var entry : entries) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException("Each book must be an object");
            }
            JsonObject book = entry.getAsJsonObject();
            String id = requiredText(book, "id");
            if (!id.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Book ID may only contain letters, numbers, and underscores");
            }
            String name = requiredText(book, "name");
            int level = requiredInteger(book, "level");
            int sellLevel = requiredInteger(book, "sellLevel");
            double instaSellPercentage = requiredPercentage(book, "instaSellPercentage");
            double instaBuyPercentage = requiredPercentage(book, "instaBuyPercentage");
            if (level < 1 || sellLevel < level || sellLevel > 10) {
                throw new IllegalArgumentException("Book levels must be between 1 and 10, with sell level at least the buy level");
            }
            if (!uniqueEntries.add(id + ":" + level)) {
                throw new IllegalArgumentException("Book ID and level combinations must be unique");
            }
            books.add(new Book(id, level, sellLevel, name, instaSellPercentage, instaBuyPercentage));
        }
        return books;
    }

    private static double requiredPercentage(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException(key + " must be between 0 and 100");
        }
        return value;
    }

    private static <T> T onClientThread(Minecraft minecraft, java.util.concurrent.Callable<T> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                result.complete(operation.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private record ErrorResponse(String error) {
    }
}

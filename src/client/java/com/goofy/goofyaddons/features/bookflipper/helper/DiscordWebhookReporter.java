package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.GoofyAddons;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DiscordWebhookReporter {
    private static final int DEFAULT_REPORT_INTERVAL_SECONDS = 300;
    private static final int DEFAULT_CHART_RANGE_SECONDS = 86400;
    private static final int MAX_ACTIVITY_ENTRIES = 50;
    private static final int CHART_WIDTH = 900;
    private static final int CHART_HEIGHT = 440;
    private static final Color BACKGROUND = new Color(22, 27, 36);
    private static final Color GRID = new Color(65, 75, 90);
    private static final Color TEXT = new Color(225, 232, 240);
    private static final Color MUTED = new Color(155, 168, 184);
    private static final Color REALIZED = new Color(85, 214, 190);
    private static final Color UNREALIZED = new Color(242, 187, 102);

    private final HttpClient client = HttpClient.newBuilder().build();
    private final List<String> ordered = new ArrayList<>();
    private final List<String> bought = new ArrayList<>();
    private final List<String> sold = new ArrayList<>();
    private final AtomicBoolean sending = new AtomicBoolean();
    private long nextReportAt;
    private String trackedWebhookUrl = "";
    private int trackedReportIntervalSeconds;
    private double peakBuyOrderCoins;

    public synchronized void start() {
        ordered.clear();
        bought.clear();
        sold.clear();
        peakBuyOrderCoins = 0;
        nextReportAt = 0;
        trackedWebhookUrl = "";
        trackedReportIntervalSeconds = 0;
    }

    public synchronized void recordOrderPlaced(String description) {
        addActivity(ordered, description);
    }

    public synchronized void recordBought(String description) {
        addActivity(bought, description);
    }

    public synchronized void recordSold(String description) {
        addActivity(sold, description);
    }

    public void onTick(double activeBuyOrderCoins) {
        String webhookUrl = GoofyConfig.INSTANCE.discordWebhookUrl == null
                ? "" : GoofyConfig.INSTANCE.discordWebhookUrl.trim();
        if (!GoofyConfig.INSTANCE.discordWebhookEnabled || webhookUrl.isEmpty()) {
            synchronized (this) {
                nextReportAt = 0;
                trackedWebhookUrl = "";
            }
            return;
        }

        int reportIntervalSeconds = supportedReportInterval(GoofyConfig.INSTANCE.discordWebhookIntervalSeconds);
        int chartRangeSeconds = supportedChartRange(GoofyConfig.INSTANCE.discordWebhookChartRangeSeconds);
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (nextReportAt == 0 || !trackedWebhookUrl.equals(webhookUrl)
                    || trackedReportIntervalSeconds != reportIntervalSeconds) {
                trackedWebhookUrl = webhookUrl;
                trackedReportIntervalSeconds = reportIntervalSeconds;
                nextReportAt = now + reportIntervalSeconds * 1000L;
            }
            peakBuyOrderCoins = Math.max(peakBuyOrderCoins, activeBuyOrderCoins);
            if (now < nextReportAt || !sending.compareAndSet(false, true)) return;
            nextReportAt = now + reportIntervalSeconds * 1000L;
        }

        ProfitTracker.Snapshot snapshot = ProfitTracker.INSTANCE.snapshot();
        List<ProfitTracker.ProfitPoint> realizedHistory = snapshot.realizedHistory();
        List<ProfitTracker.ProfitPoint> unrealizedHistory = snapshot.unrealizedHistory();
        byte[] chart;
        try {
            chart = createChart(realizedHistory, unrealizedHistory, snapshot.realizedProfit(),
                    snapshot.unrealizedProfit(), now, chartRangeSeconds, GoofyConfig.INSTANCE.roundGraphs);
        } catch (IOException exception) {
            sending.set(false);
            GoofyAddons.LOGGER.error("Could not create the Discord profit chart", exception);
            return;
        }

        List<String> orderedCopy;
        List<String> boughtCopy;
        List<String> soldCopy;
        double peak;
        synchronized (this) {
            orderedCopy = List.copyOf(ordered);
            boughtCopy = List.copyOf(bought);
            soldCopy = List.copyOf(sold);
            peak = peakBuyOrderCoins;
        }

        sendReport(webhookUrl, snapshot, orderedCopy, boughtCopy, soldCopy, peak, chart, reportIntervalSeconds)
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        synchronized (DiscordWebhookReporter.this) {
                            ordered.subList(0, Math.min(orderedCopy.size(), ordered.size())).clear();
                            bought.subList(0, Math.min(boughtCopy.size(), bought.size())).clear();
                            sold.subList(0, Math.min(soldCopy.size(), sold.size())).clear();
                            peakBuyOrderCoins = Math.max(0, activeBuyOrderCoins);
                        }
                    } else {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        GoofyAddons.LOGGER.error("Could not send the Discord Bazaar update ({})",
                                cause.getClass().getSimpleName());
                    }
                    sending.set(false);
                });
    }

    private CompletableFuture<Void> sendReport(String webhookUrl, ProfitTracker.Snapshot snapshot,
                                               List<String> orderedCopy, List<String> boughtCopy,
                                               List<String> soldCopy, double peak, byte[] chart,
                                               int reportIntervalSeconds) {
        try {
            URI uri = validateWebhookUri(webhookUrl);
            String boundary = "GoofyAddons-" + UUID.randomUUID();
            JsonObject payload = new JsonObject();
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray());
            payload.add("allowed_mentions", allowedMentions);
            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Bazaar Flipper · " + formatDuration(reportIntervalSeconds) + " update");
            embed.addProperty("color", 5620926);
            embed.addProperty("timestamp", Instant.now().toString());
            embed.addProperty("description", "Peak coins committed to monitored flipper buy orders: **"
                    + formatCoins(peak) + "**\n"
                    + "Realized (claimed): **" + formatCoins(snapshot.realizedProfit()) + "** · "
                    + "Unrealized (active sell offers): **" + formatCoins(snapshot.unrealizedProfit()) + "** · "
                    + "Open flips: **" + snapshot.openFlips() + "**");
            JsonArray fields = new JsonArray();
            fields.add(field("Orders placed since last update", formatActivity(orderedCopy)));
            fields.add(field("Buy orders filled since last update", formatActivity(boughtCopy)));
            fields.add(field("Sell orders filled since last update", formatActivity(soldCopy)));
            embed.add("fields", fields);
            JsonObject image = new JsonObject();
            image.addProperty("url", "attachment://profit.png");
            embed.add("image", image);
            embeds.add(embed);
            payload.add("embeds", embeds);

            byte[] body = multipart(boundary, payload.toString(), chart);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new IllegalStateException("Discord returned HTTP " + response.statusCode());
                        }
                        return null;
                    });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static URI validateWebhookUri(String webhookUrl) {
        URI uri = URI.create(webhookUrl);
        String[] parts = uri.getPath().split("/");
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !("discord.com".equalsIgnoreCase(uri.getHost())
                || "discordapp.com".equalsIgnoreCase(uri.getHost()))
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || parts.length != 5 || !"api".equals(parts[1]) || !"webhooks".equals(parts[2])
                || !parts[3].matches("\\d+") || !parts[4].matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Discord webhook URL must be an HTTPS Discord webhook URL");
        }
        return uri;
    }

    private static JsonObject field(String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", false);
        return field;
    }

    private static String formatActivity(List<String> activity) {
        if (activity.isEmpty()) return "None";
        StringBuilder result = new StringBuilder();
        int shown = Math.min(activity.size(), 15);
        int included = 0;
        for (int i = 0; i < shown; i++) {
            String line = (included > 0 ? "\n" : "") + "• " + activity.get(i);
            if (result.length() + line.length() > 900) break;
            result.append(line);
            included++;
        }
        int omitted = activity.size() - included;
        if (omitted > 0) result.append("\n…and ").append(omitted).append(" more");
        return result.toString();
    }

    private synchronized void addActivity(List<String> activities, String description) {
        if (activities.size() == MAX_ACTIVITY_ENTRIES) activities.removeFirst();
        activities.add(description);
    }

    private static String formatCoins(double coins) {
        return String.format(java.util.Locale.ROOT, "%,.2f coins", coins);
    }

    private static String formatCompact(double coins) {
        double magnitude = Math.abs(coins);
        if (magnitude >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fB", coins / 1_000_000_000);
        if (magnitude >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", coins / 1_000_000);
        if (magnitude >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", coins / 1_000);
        return String.format(java.util.Locale.ROOT, "%.0f", coins);
    }

    private static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return seconds / 60 + "m";
        return seconds / 3600 + "h";
    }

    private static int supportedReportInterval(int seconds) {
        return switch (seconds) {
            case 30, 60, 300, 900, 3600 -> seconds;
            default -> DEFAULT_REPORT_INTERVAL_SECONDS;
        };
    }

    private static int supportedChartRange(int seconds) {
        return switch (seconds) {
            case 30, 60, 300, 900, 3600, 21600, 86400 -> seconds;
            default -> DEFAULT_CHART_RANGE_SECONDS;
        };
    }

    private static byte[] multipart(String boundary, String payload, byte[] chart) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "payload_json", "application/json", payload.getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"profit.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(chart);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream body, String boundary, String name,
                                  String contentType, byte[] content) throws IOException {
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] createChart(List<ProfitTracker.ProfitPoint> realizedHistory,
                                      List<ProfitTracker.ProfitPoint> unrealizedHistory,
                                      double currentRealized, double currentUnrealized, long now,
                                      int chartRangeSeconds, boolean roundGraphs) throws IOException {
        long from = now - chartRangeSeconds * 1000L;
        List<ProfitTracker.ProfitPoint> realizedPoints = pointsForChart(realizedHistory, currentRealized, from, now);
        List<ProfitTracker.ProfitPoint> unrealizedPoints = pointsForChart(unrealizedHistory, currentUnrealized, from, now);
        double max = Math.max(1, Math.max(
                realizedPoints.stream().mapToDouble(ProfitTracker.ProfitPoint::value).max().orElse(0),
                unrealizedPoints.stream().mapToDouble(ProfitTracker.ProfitPoint::value).max().orElse(0)));
        BufferedImage image = new BufferedImage(CHART_WIDTH, CHART_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, CHART_WIDTH, CHART_HEIGHT);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.setColor(TEXT);
            graphics.drawString("Realized and unrealized Bazaar profits · last "
                    + formatDuration(chartRangeSeconds), 28, 34);

            int left = 90, right = 28, top = 72, bottom = 72;
            int plotWidth = CHART_WIDTH - left - right, plotHeight = CHART_HEIGHT - top - bottom;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            for (int i = 0; i <= 4; i++) {
                int y = top + plotHeight * i / 4;
                graphics.setColor(GRID);
                graphics.drawLine(left, y, CHART_WIDTH - right, y);
                graphics.setColor(MUTED);
                graphics.drawString(formatCompact(max * (4 - i) / 4), 22, y + 5);
            }
            drawLine(graphics, realizedPoints, REALIZED, from, now, max, left, top, plotWidth, plotHeight, roundGraphs);
            drawLine(graphics, unrealizedPoints, UNREALIZED, from, now, max, left, top, plotWidth, plotHeight, roundGraphs);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            graphics.setColor(REALIZED);
            graphics.fillOval(left, CHART_HEIGHT - 42, 12, 12);
            graphics.drawString("Realized · " + formatCoins(currentRealized), left + 20, CHART_HEIGHT - 31);
            graphics.setColor(UNREALIZED);
            int legendX = left + 390;
            graphics.fillOval(legendX, CHART_HEIGHT - 42, 12, 12);
            graphics.drawString("Unrealized · " + formatCoins(currentUnrealized), legendX + 20, CHART_HEIGHT - 31);
            graphics.setColor(MUTED);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            graphics.drawString(formatDuration(chartRangeSeconds) + " ago", left, CHART_HEIGHT - 54);
            graphics.drawString("Now", CHART_WIDTH - right - 26, CHART_HEIGHT - 54);
            graphics.drawString("Unrealized is the total value listed on active sell offers.", left, CHART_HEIGHT - 10);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder is unavailable");
        return output.toByteArray();
    }

    private static List<ProfitTracker.ProfitPoint> pointsForChart(List<ProfitTracker.ProfitPoint> history,
                                                                   double current, long from, long now) {
        List<ProfitTracker.ProfitPoint> points = new ArrayList<>();
        for (ProfitTracker.ProfitPoint point : history) {
            if (point.timestamp() >= from && point.timestamp() <= now) points.add(point);
        }
        if (points.isEmpty() || points.getLast().timestamp() < now) {
            points.add(new ProfitTracker.ProfitPoint(now, current));
        }
        return points;
    }

    private static void drawLine(Graphics2D graphics, List<ProfitTracker.ProfitPoint> points, Color color,
                                 long from, long now, double max, int left, int top, int width, int height,
                                 boolean roundGraphs) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        List<Point> coordinates = new ArrayList<>();
        for (ProfitTracker.ProfitPoint point : points) {
            int x = left + (int) ((point.timestamp() - from) * width / (double) (now - from));
            int y = top + height - (int) (Math.max(0, point.value()) / max * height);
            coordinates.add(new Point(x, y));
            graphics.fillOval(x - 3, y - 3, 6, 6);
        }
        if (coordinates.size() < 2) return;
        if (!roundGraphs) {
            for (int i = 1; i < coordinates.size(); i++) {
                Point previous = coordinates.get(i - 1);
                Point current = coordinates.get(i);
                graphics.drawLine(previous.x, previous.y, current.x, current.y);
            }
            return;
        }
        Path2D path = new Path2D.Double();
        Point first = coordinates.getFirst();
        path.moveTo(first.x, first.y);
        for (int i = 1; i < coordinates.size() - 1; i++) {
            Point current = coordinates.get(i);
            Point next = coordinates.get(i + 1);
            path.quadTo(current.x, current.y, (current.x + next.x) / 2.0, (current.y + next.y) / 2.0);
        }
        Point last = coordinates.getLast();
        path.lineTo(last.x, last.y);
        graphics.draw(path);
    }
}

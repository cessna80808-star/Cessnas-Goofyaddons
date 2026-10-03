package com.goofy.goofyaddons.render.gui;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.keybinds.GoofyKeybinds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GoofyGui extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 360;
    private static final int PANEL_COLOR = 0xF0141822;
    private static final int CARD_COLOR = 0xFF202734;
    private static final int ACCENT_COLOR = 0xFF55D6BE;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private boolean speedMode;
    private String saveMessage = "";
    private int saveMessageColor = 0xFFB7C2D0;

    private EditBox speedDelayField;
    private EditBox minDelayField;
    private EditBox maxDelayField;
    private EditBox firstPageField;
    private EditBox secondPageField;

    public GoofyGui() {
        super(Component.literal("Goofy Addons"));
    }

    @Override
    protected void init() {
        super.init();
        GoofyConfig config = GoofyConfig.INSTANCE;
        if (config == null) return;

        panelWidth = Math.clamp(width - 16, 300, PANEL_WIDTH);
        panelHeight = Math.clamp(height - 16, 220, PANEL_HEIGHT);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        speedMode = config.speedMode;

        int leftX = panelX + 16;
        int cardY = panelY + 56;
        int leftWidth = (panelWidth - 48) / 2;
        int rightX = leftX + leftWidth + 16;
        int rightWidth = panelX + panelWidth - 16 - rightX;

        addRenderableWidget(Button.builder(Component.literal("Start"), button -> {
            FeatureManager.INSTANCE.start("BazaarFlipper");
            button.setMessage(Component.literal("Running"));
            saveMessage = "Macro started";
            saveMessageColor = ACCENT_COLOR;
        }).bounds(leftX + 8, panelY + panelHeight - 34, (leftWidth - 24) / 2, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> {
            FeatureManager.INSTANCE.stop();
            button.setMessage(Component.literal("Stopped"));
            saveMessage = "Macro stopped";
            saveMessageColor = ACCENT_COLOR;
        }).bounds(leftX + 16 + (leftWidth - 24) / 2, panelY + panelHeight - 34, (leftWidth - 24) / 2, 22).build());

        addRenderableWidget(Button.builder(speedModeLabel(), button -> {
            speedMode = !speedMode;
            button.setMessage(speedModeLabel());
        }).bounds(rightX + rightWidth - 112, cardY + 13, 104, 20).build());

        int fieldX = rightX + 8;
        int fieldWidth = rightWidth - 16;
        speedDelayField = createField(fieldX, cardY + 66, fieldWidth, String.valueOf(config.speedModeDelay));
        minDelayField = createField(fieldX, cardY + 132, Math.max(40, (fieldWidth - 12) / 2), String.valueOf(config.minActionDelay));
        maxDelayField = createField(fieldX + (fieldWidth + 12) / 2, cardY + 132, Math.max(40, (fieldWidth - 12) / 2), String.valueOf(config.maxActionDelay));
        firstPageField = createField(fieldX, cardY + 170, fieldWidth, config.firstPage);
        secondPageField = createField(fieldX, cardY + 208, fieldWidth, config.secondPage);

        addRenderableWidget(Button.builder(Component.literal("Save settings"), button -> {
            saveSettings();
            button.setMessage(Component.literal(saveMessageColor == ACCENT_COLOR ? "Saved" : "Save failed"));
        })
                .bounds(rightX + rightWidth - 112, panelY + panelHeight - 34, 104, 22).build());
    }

    private EditBox createField(int x, int y, int fieldWidth, String value) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 20, Component.empty());
        field.setMaxLength(128);
        field.setValue(value == null ? "" : value);
        return addRenderableWidget(field);
    }

    private Component speedModeLabel() {
        return Component.literal("Speed: " + (speedMode ? "ON" : "OFF"));
    }

    private void saveSettings() {
        String speedText = speedDelayField.getValue().trim();
        String minText = minDelayField.getValue().trim();
        String maxText = maxDelayField.getValue().trim();
        String firstPage = firstPageField.getValue().trim();
        String secondPage = secondPageField.getValue().trim();

        if (isInvalidInteger(speedText) || isInvalidInteger(minText) || isInvalidInteger(maxText)) {
            showSaveMessage("Delays must be whole numbers", false);
            return;
        }
        if (firstPage.isEmpty() || secondPage.isEmpty()) {
            showSaveMessage("Bazaar commands cannot be empty", false);
            return;
        }

        int speedDelay;
        int minDelay;
        int maxDelay;
        try {
            speedDelay = Integer.parseInt(speedText);
            minDelay = Integer.parseInt(minText);
            maxDelay = Integer.parseInt(maxText);
        } catch (NumberFormatException exception) {
            showSaveMessage("Delay is outside the supported range", false);
            return;
        }
        if (speedDelay < 1 || minDelay < 51 || maxDelay <= minDelay) {
            showSaveMessage("Speed delay must be positive; action min must be 51+ and max greater", false);
            return;
        }

        GoofyConfig config = GoofyConfig.INSTANCE;
        config.speedMode = speedMode;
        config.speedModeDelay = speedDelay;
        config.minActionDelay = minDelay;
        config.maxActionDelay = maxDelay;
        config.firstPage = firstPage;
        config.secondPage = secondPage;
        if (!GoofyConfig.save()) {
            showSaveMessage("Could not save settings; check the log", false);
            return;
        }
        showSaveMessage("Settings saved", true);
    }

    private boolean isInvalidInteger(String value) {
        return !value.matches("\\d+");
    }

    private void showSaveMessage(String message, boolean success) {
        saveMessage = message;
        saveMessageColor = success ? ACCENT_COLOR : 0xFFFF7777;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF3A4657);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 3, ACCENT_COLOR);
        graphics.text(font, "GOOFY ADDONS", panelX + 16, panelY + 14, 0xFFFFFFFF);
        graphics.text(font, "BAZAAR FLIPPER  /  CONTROL PANEL", panelX + 16, panelY + 34, 0xFF9BA8B8);
        drawKeybinds(graphics);

        int leftX = panelX + 16;
        int cardY = panelY + 56;
        int leftWidth = (panelWidth - 48) / 2;
        int rightX = leftX + leftWidth + 16;
        int rightWidth = panelX + panelWidth - 16 - rightX;
        graphics.fill(leftX, cardY, leftX + leftWidth, panelY + panelHeight - 44, CARD_COLOR);
        graphics.fill(rightX, cardY, rightX + rightWidth, panelY + panelHeight - 44, CARD_COLOR);

        drawOverview(graphics, leftX, cardY, leftWidth);
        drawSettings(graphics, rightX, cardY, rightWidth);
        graphics.text(font, saveMessage, rightX + 8, cardY + 235, saveMessageColor);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawOverview(GuiGraphicsExtractor graphics, int x, int y, int cardWidth) {
        boolean running = FeatureManager.INSTANCE.isMacroRunning();
        graphics.text(font, "LIVE STATUS", x + 10, y + 10, ACCENT_COLOR);
        graphics.fill(x + 10, y + 29, x + cardWidth - 10, y + 30, 0xFF394454);
        graphics.text(font, "Macro", x + 10, y + 40, 0xFF9BA8B8);
        graphics.text(font, running ? "RUNNING" : "STOPPED", x + 78, y + 40, running ? ACCENT_COLOR : 0xFFFFA36C);
        graphics.text(font, "Feature", x + 10, y + 58, 0xFF9BA8B8);
        graphics.text(font, FeatureManager.INSTANCE.currentFeatureName(), x + 78, y + 58, 0xFFFFFFFF);
        graphics.text(font, "State", x + 10, y + 76, 0xFF9BA8B8);
        graphics.text(font, FeatureManager.INSTANCE.currentFeatureStatus(), x + 78, y + 76, 0xFFFFFFFF);
        graphics.text(font, "Active tasks", x + 10, y + 94, 0xFF9BA8B8);
        graphics.text(font, String.valueOf(FeatureManager.INSTANCE.currentFeatureTaskCount()), x + 78, y + 94, 0xFFFFFFFF);

        var activeFailsafes = FailsafeManager.INSTANCE.activeFailsafes();
        graphics.text(font, "Failsafe", x + 10, y + 112, 0xFF9BA8B8);
        graphics.text(font, activeFailsafes.isEmpty() ? "None" : String.join(", ", activeFailsafes), x + 78, y + 112, activeFailsafes.isEmpty() ? 0xFFFFFFFF : 0xFFFFC26C);
        graphics.fill(x + 10, y + 132, x + cardWidth - 10, y + 133, 0xFF394454);
        graphics.text(font, "CONFIGURED BOOKS  (" + GoofyConfig.INSTANCE.books.size() + ")", x + 10, y + 143, ACCENT_COLOR);

        int bookY = y + 162;
        int listBottom = panelY + panelHeight - 53;
        int visibleBooks = Math.max(0, (listBottom - bookY) / 42);
        int displayedBooks = Math.min(GoofyConfig.INSTANCE.books.size(), visibleBooks);
        if (GoofyConfig.INSTANCE.books.size() > displayedBooks) {
            displayedBooks = Math.max(0, displayedBooks - 1);
        }
        for (int i = 0; i < displayedBooks; i++) {
            var book = GoofyConfig.INSTANCE.books.get(i);
            int rowY = bookY + i * 42;
            graphics.text(font, book.name() + " " + book.level() + " -> " + book.sellLevel(), x + 10, rowY, 0xFFE4EAF2);
            graphics.text(font, book.id(), x + 10, rowY + 12, 0xFF9BA8B8);
            graphics.text(font, "Buy " + book.instaBuyPercentage() + "% / Sell " + book.instaSellPercentage() + "%",
                    x + 10, rowY + 24, 0xFF9BA8B8);
        }
        if (GoofyConfig.INSTANCE.books.size() > displayedBooks) {
            graphics.text(font, "+" + (GoofyConfig.INSTANCE.books.size() - displayedBooks) + " more books",
                    x + 10, bookY + displayedBooks * 42, 0xFF9BA8B8);
        } else if (GoofyConfig.INSTANCE.books.isEmpty()) {
            graphics.text(font, "No books configured", x + 10, bookY, 0xFF9BA8B8);
        }
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int x, int y, int cardWidth) {
        int labelX = x + 8;
        graphics.text(font, "FLIPPER SETTINGS", labelX, y + 10, ACCENT_COLOR);
        graphics.text(font, "Speed mode", labelX, y + 39, 0xFFE4EAF2);
        graphics.text(font, "Fixed delay in speed mode (ms)", labelX, y + 59, 0xFF9BA8B8);
        graphics.text(font, "Action delay range (ms)", labelX, y + 99, 0xFF9BA8B8);
        graphics.text(font, "Minimum", labelX, y + 118, 0xFF9BA8B8);
        graphics.text(font, "Maximum", labelX + (cardWidth - 4) / 2, y + 118, 0xFF9BA8B8);
        graphics.text(font, "First storage page command", labelX, y + 158, 0xFF9BA8B8);
        graphics.text(font, "Second storage page command", labelX, y + 196, 0xFF9BA8B8);
    }

    private void drawKeybinds(GuiGraphicsExtractor graphics) {
        String text = "Keys: Start " + GoofyKeybinds.startKey.getTranslatedKeyMessage().getString()
                + "  /  Stop " + GoofyKeybinds.stopKey.getTranslatedKeyMessage().getString()
                + "  /  Menu " + GoofyKeybinds.openMenuKey.getTranslatedKeyMessage().getString();
        graphics.text(font, text, panelX + panelWidth - 16 - font.width(text), panelY + 16, 0xFF9BA8B8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

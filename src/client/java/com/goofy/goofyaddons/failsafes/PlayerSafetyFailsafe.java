package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.render.gui.GoofyGui;
import com.goofy.goofyaddons.utils.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public final class PlayerSafetyFailsafe implements Failsafe {
    private static final long DISCONNECTED_GRACE_MS = 3000;
    private static final double TELEPORT_DISTANCE = 32;

    private final Minecraft minecraft = Minecraft.getInstance();
    private boolean active;
    private String reason = "PlayerSafety";
    private double lastX;
    private double lastY;
    private double lastZ;
    private ClientLevel lastLevel;
    private long missingWorldSince;

    @Override
    public String name() {
        return reason;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean runsWhilePaused() {
        return true;
    }

    @Override
    public void onTick() {
        if (active || !FeatureManager.INSTANCE.isMacroRunning()
                || "Paused".equals(FeatureManager.INSTANCE.currentFeatureStatus())) {
            resetObservation();
            return;
        }

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            resetMovement();
            if (missingWorldSince == 0) missingWorldSince = System.currentTimeMillis();
            if (System.currentTimeMillis() - missingWorldSince >= DISCONNECTED_GRACE_MS) {
                trigger("Connection Lost", "Player or world unavailable");
            }
            return;
        }
        missingWorldSince = 0;

        if (lastLevel != null && lastLevel != level) {
            trigger("Teleport/World Change", "World changed while the macro was active");
            return;
        }

        if (lastLevel != null) {
            double dx = player.getX() - lastX;
            double dy = player.getY() - lastY;
            double dz = player.getZ() - lastZ;
            if (dx * dx + dy * dy + dz * dz >= TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                trigger("Teleport/World Change", "Large player position change detected");
                return;
            }
        }
        rememberPosition(player, level);

        Screen screen = minecraft.screen;
        if (screen == null || isExpectedScreen(screen)) {
            return;
        }

        Component title = screen.getTitle();
        trigger("Unexpected GUI", "Unexpected screen opened: " + title.getString());
    }

    @Override
    public void reset() {
        active = false;
        reason = "PlayerSafety";
        resetObservation();
    }

    private boolean isExpectedScreen(Screen screen) {
        if (screen instanceof GoofyGui || screen instanceof AbstractSignEditScreen) return true;

        String title = screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT);
        if (title.contains("bazaar") || title.contains("confirm") || title.contains("order")
                || title.contains("ender chest") || title.contains("backpack") || title.contains("anvil")
                || title.contains("repair & name")
                || title.contains("how many do you want") || title.contains("how much do you want to pay")
                || title.contains("at what price are you selling")) {
            return true;
        }
        return GoofyConfig.INSTANCE.books.stream()
                .anyMatch(book -> title.contains(book.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void trigger(String failsafe, String details) {
        active = true;
        reason = failsafe;
        resetObservation();
        ChatUtils.debugMessage("[Failsafe] " + details);
        FailsafeManager.INSTANCE.pauseForSafety(failsafe + ": " + details);
    }

    private void rememberPosition(LocalPlayer player, ClientLevel level) {
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        lastLevel = level;
    }

    private void resetMovement() {
        lastLevel = null;
    }

    private void resetObservation() {
        resetMovement();
        missingWorldSince = 0;
    }
}

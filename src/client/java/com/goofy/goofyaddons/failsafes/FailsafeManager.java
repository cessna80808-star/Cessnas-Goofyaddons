package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.GoofyAddons;
import com.goofy.goofyaddons.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;

public class FailsafeManager {
    List<Failsafe> failsafes = new ArrayList<>();

    public static FailsafeManager INSTANCE = new FailsafeManager();
    private boolean safetyPaused;

    private FailsafeManager() {
        failsafes.add(new ScheduledReboot());
        failsafes.add(new PlayerSafetyFailsafe());
    }

    public void onTick() {
        if (!FeatureManager.INSTANCE.hasFeature()) return;
        failsafes.stream()
                .filter(failsafe -> FeatureManager.INSTANCE.isMacroRunning() || failsafe.runsWhilePaused())
                .forEach(Failsafe::onTick);
    }

    public List<String> activeFailsafes() {
        return failsafes.stream().filter(Failsafe::isActive).map(Failsafe::name).toList();
    }

    public void pauseForSafety(String reason) {
        if (safetyPaused) return;
        safetyPaused = true;
        FeatureManager.INSTANCE.pause();
        GoofyAddons.LOGGER.warn("Failsafe paused the macro: {}", reason);
        ChatUtils.clientMessage("Failsafe: " + reason + ". Macro paused; check your surroundings and press Start to resume safely.");
    }

    public boolean isSafetyPaused() {
        return safetyPaused;
    }

    public void clearSafetyPause() {
        safetyPaused = false;
        failsafes.forEach(Failsafe::reset);
    }

}

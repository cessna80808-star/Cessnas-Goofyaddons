package com.goofy.goofyaddons.features;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;
import com.goofy.goofyaddons.features.bookflipper.helper.ActiveOrder;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import java.util.ArrayList;
import java.util.List;


public class FeatureManager {
    List<Feature> featureList = new ArrayList<>();
    Feature currentFeature = null;


    public static final FeatureManager INSTANCE = new FeatureManager();

    private FeatureManager() {
        featureList.add(new BazaarFlipper());
    }

    public void onTick() {
        if (currentFeature == null) return;
        currentFeature.onTick();
    }

    public void start(String name) {
        Feature selected = featureList.stream().filter(feature -> feature.name().equals(name)).findFirst().orElse(null);
        if (selected == null) return;
        if (currentFeature == selected) {
            if (FailsafeManager.INSTANCE.isSafetyPaused()) {
                currentFeature.resume();
                FailsafeManager.INSTANCE.clearSafetyPause();
            }
            return;
        }
        if (currentFeature != null) currentFeature.stop();
        FailsafeManager.INSTANCE.clearSafetyPause();
        currentFeature = selected;
        currentFeature.start();
    }

    public void stop() {
        if (currentFeature != null) {
            currentFeature.stop();
            currentFeature = null;
        }
        FailsafeManager.INSTANCE.clearSafetyPause();
    }

    public void pause() {
        if (currentFeature == null) return;
        currentFeature.pause();

    }

    public void resume() {
        if (currentFeature == null) return;
        currentFeature.resume();
    }

    public boolean isMacroRunning() {
        return currentFeature != null && currentFeature.isRunning();
    }

    public boolean hasFeature() {
        return currentFeature != null;
    }

    public String currentFeatureName() {
        return currentFeature == null ? "None" : currentFeature.name();
    }

    public String currentFeatureStatus() {
        return currentFeature == null ? "Stopped" : currentFeature.status();
    }

    public int currentFeatureTaskCount() {
        return currentFeature == null ? 0 : currentFeature.activeTaskCount();
    }

    public List<ActiveOrder> activeOrders() {
        return featureList.stream()
                .filter(BazaarFlipper.class::isInstance)
                .map(BazaarFlipper.class::cast)
                .findFirst()
                .map(BazaarFlipper::activeOrders)
                .orElseGet(List::of);
    }

}

package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.utils.Clock;
import net.minecraft.client.Minecraft;

public class ScheduledReboot implements Failsafe {
    enum State {
        ISLAND,
        HUB,
        COMPLETED
    }

    private boolean enabled = false;
    private State state;
    private final Clock clock = new Clock();


    public ScheduledReboot() {
        ChatHook.onMessage("Scheduled Reboot", this::handleMessage);
        ChatHook.onMessage("Game Update", this::handleMessage);
    }

    @Override
    public String name() {
        return "ScheduledReboot";
    }

    @Override
    public boolean isActive() {
        return enabled;
    }

    @Override
    public boolean runsWhilePaused() {
        return true;
    }

    @Override
    public void reset() {
        enabled = false;
        state = null;
        clock.stop();
    }

    @Override
    public void onTick() {
        if (!enabled) return;

        switch (state) {
            case ISLAND -> {
                if (Minecraft.getInstance().player == null) return;
                Minecraft.getInstance().player.connection.sendCommand("Hub");
                state = State.HUB;
            }

            case HUB -> {
                clock.start(10000);
                if (clock.shouldFire()) {
                    if (Minecraft.getInstance().player == null) return;
                    Minecraft.getInstance().player.connection.sendCommand("Is");
                    state = State.COMPLETED;
                }
            }

            case COMPLETED -> {
                clock.start(5000);
                if (clock.shouldFire()) {
                    if (!FailsafeManager.INSTANCE.isSafetyPaused()) {
                        FeatureManager.INSTANCE.resume();
                    }
                    enabled = false;
                }
            }

        }

    }

    private void handleMessage(String message) {
        FeatureManager.INSTANCE.pause();
        enabled = true;
        state = State.ISLAND;
    }
}

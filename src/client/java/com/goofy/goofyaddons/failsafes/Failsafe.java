package com.goofy.goofyaddons.failsafes;

public interface Failsafe {
    String name();

    default boolean isActive() {
        return false;
    }

    default void reset() {
    }

    default boolean runsWhilePaused() {
        return false;
    }

    void onTick();
}

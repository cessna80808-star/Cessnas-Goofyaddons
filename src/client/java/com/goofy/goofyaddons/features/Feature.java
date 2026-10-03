package com.goofy.goofyaddons.features;

public interface Feature {
    String name();

    default String status() {
        return "Ready";
    }

    default boolean isRunning() {
        return true;
    }

    default int activeTaskCount() {
        return 0;
    }

    void stop();

    void start();

    void pause();

    void resume();

    void onTick();
}

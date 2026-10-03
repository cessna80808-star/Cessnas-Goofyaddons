package com.goofy.goofyaddons;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.keybinds.GoofyKeybinds;
import com.goofy.goofyaddons.render.gui.GoofyGui;
import com.goofy.goofyaddons.web.WebGuiServer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;


public class GoofyAddonsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GoofyConfig.load();
        ChatHook.register();
        GoofyKeybinds.register();
        WebGuiServer.start(Minecraft.getInstance());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FailsafeManager.INSTANCE.onTick();
            FeatureManager.INSTANCE.onTick();

            if (InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_BACKSLASH)) GoofyConfig.load();

            while (GoofyKeybinds.startKey.consumeClick()) {
                FeatureManager.INSTANCE.start("BazaarFlipper");
            }
            while (GoofyKeybinds.stopKey.consumeClick()) {
                FeatureManager.INSTANCE.stop();
            }
            while (GoofyKeybinds.openMenuKey.consumeClick()) {
                client.setScreen(new GoofyGui());
            }
        });
    }
}

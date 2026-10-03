package com.goofy.goofyaddons.event;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatHook {
    private static final List<HOOK> hookList = new ArrayList<>();

    public static void register() {
        ClientReceiveMessageEvents.GAME.register(ChatHook::onChatMessage);
    }

    public static void onMessage(String pattern, Consumer<String> string) {
        hookList.add(new HOOK(pattern, string));
    }


    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay) return;
        String text = message.getString().replaceAll("§.", "");
        if (text.startsWith("[GoofyAddons]")) return;
        String normalizedText = text.toLowerCase(java.util.Locale.ROOT);
        for (HOOK hook : hookList) {
            if (!normalizedText.contains(hook.pattern.toLowerCase(java.util.Locale.ROOT))) continue;
            hook.string.accept(text);
        }
    }

    record HOOK(String pattern, Consumer<String> string) {
    }
}

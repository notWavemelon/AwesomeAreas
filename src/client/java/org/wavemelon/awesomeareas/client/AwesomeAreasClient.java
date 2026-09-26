package org.wavemelon.awesomeareas.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.wavemelon.awesomeareas.client.gui.AreaMapScreen;
import org.wavemelon.awesomeareas.client.gui.MinimapHudOverlay;
import org.wavemelon.awesomeareas.client.render.BorderVisualizerRenderer;
import org.wavemelon.awesomeareas.network.OpenAreaMapPayload;
import org.wavemelon.awesomeareas.network.SyncAreasPayload;

public class AwesomeAreasClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("awesomeareas", "general"));
    private static KeyMapping openMapKey;
    private static KeyMapping toggleMinimapKey;
    private static KeyMapping toggleBordersKey;

    @Override
    public void onInitializeClient() {
        // Register map screen keybinding (Default: ',' comma)
        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.awesomeareas.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_COMMA,
                CATEGORY
        ));

        // Register minimap HUD toggle keybinding (Default: 'F12')
        toggleMinimapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.awesomeareas.toggle_minimap",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F12,
                CATEGORY
        ));

        // Register in-world border visualizer toggle keybinding (Default: ';' semicolon)
        toggleBordersKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.awesomeareas.toggle_borders",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                CATEGORY
        ));

        // Register Minimap HUD element
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("awesomeareas", "minimap"),
                new MinimapHudOverlay()
        );

        // Initialize in-world border rendering
        BorderVisualizerRenderer.initialize();

        // Listen for key presses to open map or toggle views
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMapKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    client.gui.setScreen(new AreaMapScreen());
                }
            }
            while (toggleMinimapKey.consumeClick()) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                MinimapHudOverlay.toggleVisibility();
            }
            while (toggleBordersKey.consumeClick()) {
                BorderVisualizerRenderer.toggle();
            }
        });

        // Clear client cache when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientAreaCache.getInstance().clear();
        });

        // Network receiver: Sync area data
        ClientPlayNetworking.registerGlobalReceiver(SyncAreasPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientAreaCache.getInstance().updateFromJson(payload.jsonData());
            });
        });

        // Network receiver: Prompt opening map screen
        ClientPlayNetworking.registerGlobalReceiver(OpenAreaMapPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                context.client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                context.client().gui.setScreen(new AreaMapScreen());
            });
        });
    }

    public static KeyMapping getOpenMapKey() {
        return openMapKey;
    }

    public static KeyMapping getToggleMinimapKey() {
        return toggleMinimapKey;
    }

    public static KeyMapping getToggleBordersKey() {
        return toggleBordersKey;
    }
}

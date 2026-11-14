package com.ezvisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.EntityHitResult;

public class EzVisuals implements ClientModInitializer {
    public static final String ID = "ezvisuals";
    public static final String FOUNDER = "ezbumhikvisual";

    public static volatile float tps = 20f;
    private static long lastNano = -1, lastWorldTime = -1;

    public static boolean zooming = false;
    public static long lastCombatMs = 0;
    public static String toast = "";
    public static long toastUntil = 0;
    private static final boolean[] wasDown = new boolean[20];
    public static boolean bridgeEnabled = false, lookSlotsEnabled = false;

    @Override
    public void onInitializeClient() {
        EzConfig.load(0);
        EzLua.init();

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> EzHud.render(ctx));
        WorldRenderEvents.LAST.register(EzPredict::render);
    }

    private void tick(MinecraftClient c) {
        if (c.player == null) { zooming = false; return; }
        EzLua.tick();
        EzConfig k = EzConfig.I;
        long now = System.currentTimeMillis();

        // combat tracking for PvP Safe
        if (c.player.hurtTime > 0) lastCombatMs = now;
        if (c.player.handSwinging && c.crosshairTarget instanceof EntityHitResult) lastCombatMs = now;

        if (c.currentScreen == null) {
            if (pressed(c, k.keyMenu, 0)) c.setScreen(new EzScreen());
            cmd(c, k.keyHome, 1, "home");
            cmd(c, k.keyArena, 2, "darena");
            cmd(c, k.keyHub, 3, "hub");
            if (pressed(c, k.keyBridge, 4)) bridgeEnabled = !bridgeEnabled;
            if (pressed(c, k.keyLookSlots, 5)) lookSlotsEnabled = !lookSlotsEnabled;
            fastSwap(c, k.keyTrap, 6, new String[]{"трап", "trap"});
            fastSwap(c, k.keyDisorient, 7, new String[]{"дезор", "дезори", "disor"});
            fastSwap(c, k.keyWeb, 8, new String[]{"паутина", "web", "сеть"});
            fastSwap(c, k.keyGapple, 9, new String[]{"золот", "яблок", "gapple"});
            fastSwap(c, k.keyTotem, 10, new String[]{"тотем", "totem"});
            zooming = k.keyZoom > 0 && InputUtil.isKeyPressed(c.getWindow().getHandle(), k.keyZoom);
        } else {
            zooming = false;
        }
    }

    private static void fastSwap(MinecraftClient c, int key, int idx, String[] words) {
        if (!pressed(c, key, idx)) return;
        for (int slot = 0; slot < 9; slot++) {
            String itemName = c.player.getInventory().getStack(slot).getName().getString().toLowerCase(java.util.Locale.ROOT);
            for (String word : words) if (itemName.contains(word)) {
                c.player.getInventory().selectedSlot = slot;
                toast("Fast Swap: " + itemName);
                return;
            }
        }
        toast("Fast Swap: item not found");
    }

    public static boolean inCombat() {
        return System.currentTimeMillis() - lastCombatMs < 8000;
    }

    private static void cmd(MinecraftClient c, int key, int idx, String command) {
        if (!pressed(c, key, idx)) return;
        if (EzConfig.I.pvpSafe && inCombat()) {
            toast("PvP Safe: /" + command + " blocked (in combat)");
            return;
        }
        c.player.networkHandler.sendChatCommand(command);
    }

    private static boolean pressed(MinecraftClient c, int key, int idx) {
        if (key <= 0) { wasDown[idx] = false; return false; }
        boolean down = InputUtil.isKeyPressed(c.getWindow().getHandle(), key);
        boolean r = down && !wasDown[idx];
        wasDown[idx] = down;
        return r;
    }

    public static void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    // called from ClientPlayNetworkHandlerMixin (server sends world time ~every 20 ticks)
    public static void onTime(long worldTime) {
        long now = System.nanoTime();
        if (lastNano > 0) {
            double dt = (now - lastNano) / 1e9;
            double ticks = worldTime - lastWorldTime;
            if (dt > 0.2 && ticks > 0) tps = (float) Math.min(20.0, ticks / dt);
        }
        lastNano = now;
        lastWorldTime = worldTime;
    }
}

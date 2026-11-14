package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.*;

public class EzHud {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen instanceof EzScreen) return;
        EzConfig k = EzConfig.I;
        TextRenderer t = mc.textRenderer;
        int acc = EzConfig.accent();
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();

        if (!k.lowEnd && k.weather > 0) weather(c, w, h, k);
        if (!k.lowEnd && k.bloodVisuals) blood(c, w, h);
        if (k.itemGlow) itemGlow(c, mc, w, h);
        EzWorld.render(c, w, h);

        if (k.topBar) {
            String s = "ezvisuals  |  " + EzVisuals.FOUNDER + "  |  " + mc.getCurrentFps() + " FPS  |  "
                    + String.format(Locale.US, "%.1f", EzVisuals.tps) + " TPS  |  XYZ "
                    + (int) Math.floor(mc.player.getX()) + " " + (int) Math.floor(mc.player.getY()) + " " + (int) Math.floor(mc.player.getZ());
            int tw = t.getWidth(s);
            c.fill(6, 6, 6 + tw + 14, 22, 0xB0101018);
            c.fill(6, 6, 6 + tw + 14, 8, acc);
            c.drawText(t, s, 13, 11, 0xFFFFFFFF, true);
            if (k.pvpSafe && EzVisuals.inCombat()) {
                long left = 8000 - (System.currentTimeMillis() - EzVisuals.lastCombatMs);
                c.drawText(t, "PvP Safe: combat " + (left / 1000 + 1) + "s", 8, 26, 0xFFFF5555, true);
            }
        }

        int y = h / 2 - 40;
        if (k.armorHud) y = armor(c, mc, t, y, acc);
        if (k.totemHelper) y = totems(c, mc, t, y);
        if (k.cooldownsHud) cooldowns(c, mc, t, y + 6, acc);
        if (k.potionHud) potions(c, mc, t, w);
        if (k.nearbyPlayers) nearby(c, mc, t, acc);
        if (k.lookSlots && EzVisuals.lookSlotsEnabled && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity target) lookSlots(c, mc, t, target, acc);
        if (k.fullBridge && EzVisuals.bridgeEnabled) bridgeGuide(c, mc, t, w, h, acc);
        if (k.swimAnimation && mc.player.isSwimming()) swimIndicator(c, t, w, h, k.swimAmount, acc);
        EzLua.hud(c, t);

        if (System.currentTimeMillis() < EzVisuals.toastUntil) {
            int tw = t.getWidth(EzVisuals.toast);
            c.drawText(t, EzVisuals.toast, (w - tw) / 2, h - 60, 0xFFFF5555, true);
        }
    }

    static int armorPercent(PlayerEntity p) {
        int tot = 0, max = 0;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = p.getEquippedStack(s);
            if (!st.isEmpty() && st.isDamageable()) { tot += st.getMaxDamage() - st.getDamage(); max += st.getMaxDamage(); }
        }
        return max == 0 ? 0 : tot * 100 / max;
    }

    private static int pctColor(int pc) { return pc > 60 ? 0xFF55FF55 : pc > 30 ? 0xFFFFC83D : 0xFFFF5555; }

    private static int armor(DrawContext c, MinecraftClient mc, TextRenderer t, int y, int acc) {
        c.fill(4, y - 4, 70, y + 4 * 18 + 14, 0x90101018);
        c.fill(4, y - 4, 6, y + 4 * 18 + 14, acc);
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) {
                c.drawItem(st, 10, y);
                if (st.isDamageable()) {
                    int pc = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                    c.drawText(t, pc + "%", 30, y + 4, pctColor(pc), true);
                }
            }
            y += 18;
        }
        c.drawText(t, "Armor " + armorPercent(mc.player) + "%", 10, y + 2, acc, true);
        return y + 18;
    }

    private static int totems(DrawContext c, MinecraftClient mc, TextRenderer t, int y) {
        PlayerInventory inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) n += 1;
        boolean off = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        c.drawText(t, "Totems: " + n, 8, y, n > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
        if (n > 0 && !off) c.drawText(t, "Offhand: no totem!", 8, y + 10, 0xFFFF5555, true);
        return y + 24;
    }

    private static void cooldowns(DrawContext c, MinecraftClient mc, TextRenderer t, int y, int acc) {
        PlayerInventory inv = mc.player.getInventory();
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !seen.add(s.getItem())) continue;
            float p = mc.player.getItemCooldownManager().getCooldownProgress(s, 0f);
            if (p <= 0f) continue;
            c.drawItem(s, 6, y);
            c.fill(26, y + 6, 66, y + 10, 0xFF2A2A35);
            c.fill(26, y + 6, 26 + (int) (p * 40), y + 10, acc);
            c.drawText(t, (int) (p * 100) + "%", 70, y + 4, 0xFFDDDDDD, true);
            y += 18;
        }
    }

    private static void potions(DrawContext c, MinecraftClient mc, TextRenderer t, int w) {
        int y = 40;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString();
            boolean beneficial = e.getEffectType().value().isBeneficial();
            if (EzConfig.I.potionBeneficialOnly && !beneficial) continue;
            String time;
            if (e.isInfinite()) time = "INF";
            else {
                int seconds = Math.max(0, e.getDuration() / 20);
                time = EzConfig.I.potionTimeFormat == 1 ? seconds + "s" : String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
            }
            String line = name + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "") + "  " + time;
            int tw = t.getWidth(line);
            if (EzConfig.I.potionBackground) c.fill(w - tw - 14, y - 2, w - 4, y + 11, 0x90101018);
            c.drawText(t, line, w - tw - 8, y + 1, beneficial ? 0xFF75F59A : 0xFFFF657A, true);
            y += 15;
        }
    }

    private static void nearby(DrawContext c, MinecraftClient mc, TextRenderer t, int acc) {
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) if (p != mc.player && p.distanceTo(mc.player) < 64) list.add(p);
        list.sort(Comparator.comparingDouble(p -> p.distanceTo(mc.player)));
        int y = 40;
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            PlayerEntity p = list.get(i);
            String s = p.getName().getString() + "  " + (int) Math.ceil(p.getHealth()) + "HP  " + (EzConfig.I.armorNameTags ? armorPercent(p) + "% armor  " : "") + (int) p.distanceTo(mc.player) + "m";
            c.fill(6, y - 2, 6 + t.getWidth(s) + 8, y + 10, 0x90101018);
            c.drawText(t, s, 10, y, EzConfig.nameColor(), true);
            y += 14;
        }
    }

    private static void itemGlow(DrawContext c, MinecraftClient mc, int w, int h) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            int col;
            if (s.isIn(ItemTags.SWORDS)) col = 0xFF4040;
            else if (s.isIn(ItemTags.PICKAXES)) col = 0x40E0FF;
            else if (s.isIn(ItemTags.AXES)) col = 0xFF9A30;
            else continue;
            int x = w / 2 - 91 + i * 20, y = h - 22;
            c.fill(x, y, x + 22, y + 22, 0x30000000 | col);
            int b = 0xFF000000 | col;
            c.fill(x, y, x + 22, y + 1, b);
            c.fill(x, y + 21, x + 22, y + 22, b);
            c.fill(x, y, x + 1, y + 22, b);
            c.fill(x + 21, y, x + 22, y + 22, b);
        }
    }


    private static void lookSlots(DrawContext c, MinecraftClient mc, TextRenderer t, PlayerEntity target, int acc) {
        int x = c.getScaledWindowWidth() / 2 + 110, y = c.getScaledWindowHeight() / 2 - 42;
        c.fill(x - 5, y - 5, x + 72, y + 90, 0xB0101018);
        c.fill(x - 5, y - 5, x + 72, y - 3, acc);
        c.drawText(t, target.getName().getString(), x, y, EzConfig.nameColor(), true);
        int yy = y + 13;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = target.getEquippedStack(slot);
            c.drawItem(stack, x, yy);
            String label = stack.isEmpty() ? "Empty" : stack.getName().getString();
            if (label.length() > 9) label = label.substring(0, 9);
            c.drawText(t, label, x + 20, yy + 5, 0xFFCCCCCC, false);
            yy += 18;
        }
        c.drawText(t, "HP " + (int) target.getHealth(), x, yy + 1, acc, true);
    }

    private static void bridgeGuide(DrawContext c, MinecraftClient mc, TextRenderer t, int w, int h, int acc) {
        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty() || !(hand.getItem() instanceof net.minecraft.item.BlockItem)) return;
        int x = w / 2, y = h / 2 + 28;
        c.fill(x - 48, y - 10, x + 48, y + 20, 0xA0101018);
        c.fill(x - 48, y - 10, x + 48, y - 8, acc);
        c.drawText(t, "FULL BRIDGE", x - 34, y - 4, 0xFFFFFFFF, true);
        c.drawText(t, "Align + hold sneak", x - 42, y + 8, 0xFFBBBBBB, false);
    }

    private static void swimIndicator(DrawContext c, TextRenderer t, int w, int h, float amount, int acc) {
        int width = 28 + (int)(Math.max(0, Math.min(1, amount)) * 52);
        int x = w / 2 - width / 2, y = h - 48;
        c.fill(x, y, x + width, y + 12, 0xA0101018);
        c.fill(x, y, x + width, y + 2, acc);
        c.drawText(t, "SWIMMING", x + 5, y + 3, 0xFFFFFFFF, false);
    }

    private static void blood(DrawContext c, int w, int h) {
        // Sparse edge-only red flecks; deliberately avoids covering the center/reticle.
        for (int i = 0; i < 22; i++) {
            float x = rnd(i, 81) * w;
            float y = rnd(i, 82) * h;
            if (x > w * .28f && x < w * .72f && y > h * .22f && y < h * .78f) continue;
            int size = 1 + (int)(rnd(i, 83) * 3);
            c.fill((int)x, (int)y, (int)x + size, (int)y + size * 2, 0x66FF173D);
        }
    }

    private static float rnd(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    // 2D overlay effects (NOT real shaders): 1 rain, 2 snow, 3 space
    private static void weather(DrawContext c, int w, int h, EzConfig k) {
        float t = (System.currentTimeMillis() % 1000000L) / 1000f;
        int n = (int) k.weatherDensity;
        for (int i = 0; i < n; i++) {
            if (k.weather == 1) {
                float x = rnd(i, 1) * w;
                float y = (rnd(i, 3) * h + t * (250 + rnd(i, 2) * 200)) % h;
                c.fill((int) x, (int) y, (int) x + 1, (int) y + 9, 0x66A0C8FF);
            } else if (k.weather == 2) {
                float x = (rnd(i, 1) * w + (float) Math.sin(t * (0.5f + rnd(i, 4)) + i) * 12 + w) % w;
                float y = (rnd(i, 3) * h + t * (30 + rnd(i, 2) * 40)) % h;
                int s = 1 + (int) (rnd(i, 5) * 2);
                c.fill((int) x, (int) y, (int) x + s, (int) y + s, 0xCCFFFFFF);
            } else {
                float x = rnd(i, 1) * w, y = rnd(i, 3) * h;
                int a = 80 + (int) (175 * (0.5 + 0.5 * Math.sin(t * 2 + i)));
                int s = rnd(i, 5) > 0.85f ? 2 : 1;
                c.fill((int) x, (int) y, (int) x + s, (int) y + s, (a << 24) | 0xFFFFFF);
            }
        }
        if (k.weather == 3) c.fillGradient(0, 0, w, h, 0x300A0A30, 0x30301060);
    }
}

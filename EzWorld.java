package com.ezvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** CastomWorld: client-side visual layer (color tint, vignette, anime hero picture). Not a real shader. */
public class EzWorld {
    public static final String[] PRESET_NAMES = {"Warm sunset", "Blood moon", "Cosmic purple", "Cyber cyan", "Emerald", "GUI accent"};
    static final int[] PRESETS = {0xFF9A3C, 0xFF1744, 0x8A2BE2, 0x00E5FF, 0x3DDC84};
    public static final String[] HERO_NAMES = {"Off", "Picture 1", "Picture 2", "Picture 3", "Picture 4", "Girl (menu)"};
    public static final String[] POS_NAMES = {"Bottom right", "Bottom left", "Top right", "Top left", "Center"};

    public static void render(DrawContext c, int w, int h) {
        EzConfig k = EzConfig.I;
        if (!k.cwEnabled) return;

        int rgb = k.cwPreset >= 0 && k.cwPreset < PRESETS.length ? PRESETS[k.cwPreset] : (EzConfig.accent() & 0xFFFFFF);
        int a = (int) (Math.max(0f, Math.min(0.5f, k.cwStrength)) * 255);
        if (a > 0) c.fill(0, 0, w, h, (a << 24) | rgb);

        if (k.cwVignette > 0.01f) {
            int step = Math.max(2, h / 48);
            for (int i = 0; i < 12; i++) {
                int al = (int) (Math.min(1f, k.cwVignette) * 110 * (1f - i / 12f));
                if (al <= 0) continue;
                int col = al << 24;
                c.fill(0, i * step, w, (i + 1) * step, col);
                c.fill(0, h - (i + 1) * step, w, h - i * step, col);
                c.fill(i * step, 0, (i + 1) * step, h, col);
                c.fill(w - (i + 1) * step, 0, w - i * step, h, col);
            }
        }

        if (!k.lowEnd && k.cwHero > 0 && k.cwHero < HERO_NAMES.length) {
            boolean girl = k.cwHero == 5;
            Identifier id = girl ? EzScreen.GIRL : Identifier.of(EzVisuals.ID, "textures/gui/pic" + k.cwHero + ".png");
            if (!EzScreen.has(id)) return;
            int ph = (int) (h * Math.max(0.1f, Math.min(1f, k.cwHeroSize)));
            int pw = (int) (ph * (girl ? 262f / 504f : 0.92f));
            int m = 6;
            int x, y;
            switch (k.cwHeroPos) {
                case 1 -> { x = m; y = h - ph - m; }
                case 2 -> { x = w - pw - m; y = m; }
                case 3 -> { x = m; y = m; }
                case 4 -> { x = (w - pw) / 2; y = (h - ph) / 2; }
                default -> { x = w - pw - m; y = h - ph - m; }
            }
            int alpha = (int) (Math.max(0.05f, Math.min(1f, k.cwHeroAlpha)) * 255);
            c.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0f, 0f, pw, ph, pw, ph, (alpha << 24) | 0xFFFFFF);
        }
    }
}

package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

public class EzScreen extends Screen {
    static final int W = 600, H = 340, SIDE = 124, RH = 22, TAB_Y = 112, TAB_P = 20;
    static final String[] TABS = {"Visuals", "HUD", "FT Helper", "Effects", "Binds", "Optimize", "Configs", "Pictures", "Scripts", "CastomWorld", "Colors"};
    static final Map<Identifier, Boolean> HAS = new HashMap<>();
    static final Identifier GIRL = Identifier.of(EzVisuals.ID, "textures/gui/girl.png");

    int tab = 0, x0, y0;
    int tabY(int i) { return y0 + TAB_Y + i * TAB_P; }
    float indY = 0;
    Row listening, dragging;
    int dragX;
    final Map<Integer, List<Row>> cache = new HashMap<>();

    public EzScreen() { super(Text.literal("ezvisuals")); }

    // ---------------- widgets ----------------
    interface Row {
        void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my);
        default boolean click(int x, int y, int mx, int my) { return false; }
        default boolean draggable() { return false; }
        default void drag(int x, int mx) {}
        default boolean isBind() { return false; }
        default void setKey(int k) {}
    }

    static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    static int blend(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            r |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return r;
    }

    static Row toggle(String n, BooleanSupplier g, Consumer<Boolean> s) {
        return new Row() {
            float a = g.getAsBoolean() ? 1 : 0;
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                a += ((g.getAsBoolean() ? 1 : 0) - a) * 0.25f;
                c.drawText(t, n, x, y + 6, 0xFFDDDDDD, true);
                int tx = x + 232, ty = y + 4;
                c.fill(tx, ty, tx + 32, ty + 14, blend(0xFF2A2A35, EzConfig.accent(), a));
                int kx = tx + 2 + (int) (a * 18);
                c.fill(kx, ty + 2, kx + 10, ty + 12, 0xFFFFFFFF);
            }
            public boolean click(int x, int y, int mx, int my) {
                if (in(mx, my, x, y, 265, RH)) { s.accept(!g.getAsBoolean()); return true; }
                return false;
            }
        };
    }

    static Row slider(String n, float min, float max, Supplier<Float> g, Consumer<Float> s) {
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                float v = g.get(), f = (v - min) / (max - min);
                c.drawText(t, n, x, y + 6, 0xFFDDDDDD, true);
                c.fill(x + 120, y + 9, x + 230, y + 13, 0xFF2A2A35);
                c.fill(x + 120, y + 9, x + 120 + (int) (110 * f), y + 13, EzConfig.accent());
                int kx = x + 120 + (int) (110 * f);
                c.fill(kx - 2, y + 5, kx + 2, y + 17, 0xFFFFFFFF);
                c.drawText(t, String.format(Locale.US, "%.2f", v), x + 238, y + 6, 0xFFAAAAAA, false);
            }
            public boolean click(int x, int y, int mx, int my) {
                if (in(mx, my, x + 112, y, 126, RH)) { drag(x, mx); return true; }
                return false;
            }
            public boolean draggable() { return true; }
            public void drag(int x, int mx) {
                float f = Math.max(0, Math.min(1, (mx - (x + 120)) / 110f));
                s.accept(min + f * (max - min));
            }
        };
    }

    static Row cycle(String n, String[] opts, IntSupplier g, IntConsumer s) {
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                c.drawText(t, n, x, y + 6, 0xFFDDDDDD, true);
                boolean h = in(mx, my, x + 150, y + 2, 112, 18);
                c.fill(x + 150, y + 2, x + 262, y + 20, h ? EzConfig.accent() : 0xFF2A2A35);
                String v = opts[g.getAsInt() % opts.length];
                c.drawText(t, v, x + 150 + (112 - t.getWidth(v)) / 2, y + 7, 0xFFFFFFFF, false);
            }
            public boolean click(int x, int y, int mx, int my) {
                if (in(mx, my, x + 150, y + 2, 112, 18)) { s.accept((g.getAsInt() + 1) % opts.length); return true; }
                return false;
            }
        };
    }

    Row bind(String n, IntSupplier g, IntConsumer s) {
        EzScreen self = this;
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                c.drawText(t, n, x, y + 6, 0xFFDDDDDD, true);
                boolean l = self.listening == this;
                boolean h = in(mx, my, x + 184, y + 2, 86, 18);
                c.fill(x + 184, y + 2, x + 270, y + 20, l ? 0xFFFF3158 : h ? EzConfig.accent() : 0xFF30202A);
                int k = g.getAsInt();
                String v = l ? "PRESS KEY" : k > 0 ? InputUtil.fromKeyCode(k, 0).getLocalizedText().getString() : "UNBOUND";
                c.drawText(t, v, x + 184 + (86 - t.getWidth(v)) / 2, y + 7, 0xFFFFFFFF, false);
            }
            public boolean click(int x, int y, int mx, int my) {
                if (in(mx, my, x + 184, y + 2, 86, 18)) { self.listening = this; return true; }
                return false;
            }
            public boolean isBind() { return true; }
            public void setKey(int k) { s.accept(k); }
        };
    }

    static Row button(String n, Runnable r) {
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                boolean h = in(mx, my, x, y + 2, 140, 18);
                c.fill(x, y + 2, x + 140, y + 20, h ? EzConfig.accent() : 0xFF2A2A35);
                c.drawText(t, n, x + (140 - t.getWidth(n)) / 2, y + 7, 0xFFFFFFFF, false);
            }
            public boolean click(int x, int y, int mx, int my) {
                if (in(mx, my, x, y + 2, 140, 18)) { r.run(); return true; }
                return false;
            }
        };
    }

    static Row label(Supplier<String> s, int col) {
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                c.drawText(t, s.get(), x, y + 6, col, false);
            }
        };
    }

    static Row preview() {
        return new Row() {
            public void draw(DrawContext c, TextRenderer t, int x, int y, int mx, int my) {
                c.fill(x, y + 3, x + 16, y + 19, EzConfig.accent());
                c.drawText(t, "PlayerName  20HP  100% armor  5m", x + 24, y + 7, EzConfig.nameColor(), true);
            }
        };
    }

    List<Row> rows(int tab) {
        return cache.computeIfAbsent(tab, i -> {
            EzConfig k = EzConfig.I;
            List<Row> r = new ArrayList<>();
            switch (i) {
                case 0 -> {
                    r.add(cycle("Theme", EzConfig.THEME_NAMES, () -> EzConfig.I.theme, v -> EzConfig.I.theme = v));
                    r.add(toggle("No Fire overlay", () -> EzConfig.I.noFire, v -> EzConfig.I.noFire = v));
                    r.add(toggle("No Screen Shake", () -> EzConfig.I.noShake, v -> EzConfig.I.noShake = v));
                    r.add(toggle("Sword / Pickaxe glow", () -> EzConfig.I.itemGlow, v -> EzConfig.I.itemGlow = v));
                }
                case 1 -> {
                    r.add(toggle("Top bar (XYZ / FPS / TPS)", () -> EzConfig.I.topBar, v -> EzConfig.I.topBar = v));
                    r.add(toggle("Armor HUD (%)", () -> EzConfig.I.armorHud, v -> EzConfig.I.armorHud = v));
                    r.add(toggle("Potion timers", () -> EzConfig.I.potionHud, v -> EzConfig.I.potionHud = v));
                    r.add(cycle("Potion time", new String[]{"MM:SS", "Seconds"}, () -> EzConfig.I.potionTimeFormat, v -> EzConfig.I.potionTimeFormat = v));
                    r.add(toggle("Beneficial only", () -> EzConfig.I.potionBeneficialOnly, v -> EzConfig.I.potionBeneficialOnly = v));
                    r.add(toggle("Potion background", () -> EzConfig.I.potionBackground, v -> EzConfig.I.potionBackground = v));
                    r.add(toggle("Cooldowns", () -> EzConfig.I.cooldownsHud, v -> EzConfig.I.cooldownsHud = v));
                    r.add(toggle("Totem helper", () -> EzConfig.I.totemHelper, v -> EzConfig.I.totemHelper = v));
                    r.add(toggle("Nearby players (HP/armor)", () -> EzConfig.I.nearbyPlayers, v -> EzConfig.I.nearbyPlayers = v));
                    r.add(toggle("Look Slots (target gear)", () -> EzConfig.I.lookSlots, v -> EzConfig.I.lookSlots = v));
                    r.add(toggle("Armor Name Tags", () -> EzConfig.I.armorNameTags, v -> EzConfig.I.armorNameTags = v));
                    r.add(toggle("Full Bridge guide", () -> EzConfig.I.fullBridge, v -> EzConfig.I.fullBridge = v));
                }
                case 2 -> {
                    r.add(toggle("FT Helper (radius circles)", () -> EzConfig.I.ftHelper, v -> EzConfig.I.ftHelper = v));
                    r.add(toggle("Predictions (trajectory)", () -> EzConfig.I.predictions, v -> EzConfig.I.predictions = v));
                    r.add(toggle("PvP Safe", () -> EzConfig.I.pvpSafe, v -> EzConfig.I.pvpSafe = v));
                    r.add(slider("Disorient radius", 1, 30, () -> EzConfig.I.radDisorient, v -> EzConfig.I.radDisorient = v));
                    r.add(slider("Plast radius", 1, 30, () -> EzConfig.I.radPlast, v -> EzConfig.I.radPlast = v));
                    r.add(slider("Snowball freeze radius", 1, 15, () -> EzConfig.I.radSnowball, v -> EzConfig.I.radSnowball = v));
                    r.add(slider("Crossbow radius", 1, 15, () -> EzConfig.I.radCrossbow, v -> EzConfig.I.radCrossbow = v));
                    r.add(slider("Potion radius", 1, 15, () -> EzConfig.I.radPotion, v -> EzConfig.I.radPotion = v));
                }
                case 3 -> {
                    r.add(cycle("Weather overlay", new String[]{"Off", "Rain", "Snow", "Space"}, () -> EzConfig.I.weather, v -> EzConfig.I.weather = v));
                    r.add(slider("Density", 20, 250, () -> EzConfig.I.weatherDensity, v -> EzConfig.I.weatherDensity = v));
                    r.add(toggle("Blood visuals", () -> EzConfig.I.bloodVisuals, v -> EzConfig.I.bloodVisuals = v));
                }
                case 4 -> {
                    r.add(bind("Open GUI", () -> EzConfig.I.keyMenu, v -> EzConfig.I.keyMenu = v));
                    r.add(bind("Zoom (hold)", () -> EzConfig.I.keyZoom, v -> EzConfig.I.keyZoom = v));
                    r.add(slider("Zoom strength", 0.05f, 0.9f, () -> EzConfig.I.zoomFactor, v -> EzConfig.I.zoomFactor = v));
                    r.add(bind("/home", () -> EzConfig.I.keyHome, v -> EzConfig.I.keyHome = v));
                    r.add(bind("/darena", () -> EzConfig.I.keyArena, v -> EzConfig.I.keyArena = v));
                    r.add(bind("/hub", () -> EzConfig.I.keyHub, v -> EzConfig.I.keyHub = v));
                    r.add(bind("Full Bridge", () -> EzConfig.I.keyBridge, v -> EzConfig.I.keyBridge = v));
                    r.add(bind("Look Slots", () -> EzConfig.I.keyLookSlots, v -> EzConfig.I.keyLookSlots = v));
                    r.add(bind("Fast Swap: Trap", () -> EzConfig.I.keyTrap, v -> EzConfig.I.keyTrap = v));
                    r.add(bind("Fast Swap: Disorient", () -> EzConfig.I.keyDisorient, v -> EzConfig.I.keyDisorient = v));
                    r.add(bind("Fast Swap: Web", () -> EzConfig.I.keyWeb, v -> EzConfig.I.keyWeb = v));
                    r.add(bind("Fast Swap: GApple", () -> EzConfig.I.keyGapple, v -> EzConfig.I.keyGapple = v));
                    r.add(bind("Fast Swap: Totem", () -> EzConfig.I.keyTotem, v -> EzConfig.I.keyTotem = v));
                }
                case 5 -> {
                    r.add(toggle("Reduce particles", () -> EzConfig.I.reduceParticles, v -> EzConfig.I.reduceParticles = v));
                    r.add(slider("Particles kept %", 5, 100, () -> EzConfig.I.particleKeep, v -> EzConfig.I.particleKeep = v));
                    r.add(toggle("Low-end mode (no overlays)", () -> EzConfig.I.lowEnd, v -> EzConfig.I.lowEnd = v));
                    r.add(toggle("Swimming animation (visual toggle)", () -> EzConfig.I.swimAnimation, v -> EzConfig.I.swimAnimation = v));
                    r.add(slider("Swimming amount", 0.0f, 1.0f, () -> EzConfig.I.swimAmount, v -> EzConfig.I.swimAmount = v));
                }
                case 6 -> {
                    for (int n = 1; n <= 3; n++) {
                        final int slot = n;
                        r.add(button("Save slot " + n, () -> EzConfig.save(slot)));
                        r.add(button("Load slot " + n, () -> { EzConfig.load(slot); cache.clear(); }));
                    }
                }
                case 8 -> {
                    r.add(button("Reload scripts", () -> { EzLua.reload(); cache.remove(8); }));
                    r.add(button("Copy folder path", () -> MinecraftClient.getInstance().keyboard.setClipboard(EzLua.dir().toString())));
                    r.add(label(() -> "Folder: config/ezvisuals/scripts/*.lua", 0xFF888888));
                    for (EzLua.Script sc : EzLua.scripts) {
                        r.add(toggle(sc.name, () -> EzLua.enabled(sc), v -> EzLua.setEnabled(sc, v)));
                        if (sc.error != null) {
                            final String err = sc.error.length() > 46 ? sc.error.substring(0, 46) : sc.error;
                            r.add(label(() -> "  ! " + err, 0xFFFF5555));
                        }
                    }
                }
                case 9 -> {
                    r.add(toggle("CastomWorld", () -> EzConfig.I.cwEnabled, v -> EzConfig.I.cwEnabled = v));
                    r.add(cycle("Tint preset", EzWorld.PRESET_NAMES, () -> EzConfig.I.cwPreset, v -> EzConfig.I.cwPreset = v));
                    r.add(slider("Tint strength", 0f, 0.5f, () -> EzConfig.I.cwStrength, v -> EzConfig.I.cwStrength = v));
                    r.add(slider("Vignette", 0f, 1f, () -> EzConfig.I.cwVignette, v -> EzConfig.I.cwVignette = v));
                    r.add(cycle("Hero picture", EzWorld.HERO_NAMES, () -> EzConfig.I.cwHero, v -> EzConfig.I.cwHero = v));
                    r.add(slider("Hero opacity", 0.05f, 1f, () -> EzConfig.I.cwHeroAlpha, v -> EzConfig.I.cwHeroAlpha = v));
                    r.add(slider("Hero size", 0.1f, 1f, () -> EzConfig.I.cwHeroSize, v -> EzConfig.I.cwHeroSize = v));
                    r.add(cycle("Hero position", EzWorld.POS_NAMES, () -> EzConfig.I.cwHeroPos, v -> EzConfig.I.cwHeroPos = v));
                    r.add(label(() -> "Screen overlay (not real shaders). Use Iris for shader packs.", 0xFF888888));
                }
                case 10 -> {
                    r.add(toggle("Custom GUI color", () -> EzConfig.I.customAccent, v -> EzConfig.I.customAccent = v));
                    r.add(slider("GUI red", 0, 255, () -> EzConfig.I.accR, v -> EzConfig.I.accR = v));
                    r.add(slider("GUI green", 0, 255, () -> EzConfig.I.accG, v -> EzConfig.I.accG = v));
                    r.add(slider("GUI blue", 0, 255, () -> EzConfig.I.accB, v -> EzConfig.I.accB = v));
                    r.add(toggle("Custom name tag color", () -> EzConfig.I.customNameColor, v -> EzConfig.I.customNameColor = v));
                    r.add(slider("Name red", 0, 255, () -> EzConfig.I.nameR, v -> EzConfig.I.nameR = v));
                    r.add(slider("Name green", 0, 255, () -> EzConfig.I.nameG, v -> EzConfig.I.nameG = v));
                    r.add(slider("Name blue", 0, 255, () -> EzConfig.I.nameB, v -> EzConfig.I.nameB = v));
                    r.add(preview());
                }
                default -> {}
            }
            return r;
        });
    }

    // ---------------- rendering ----------------
    static boolean has(Identifier id) {
        return HAS.computeIfAbsent(id, i -> MinecraftClient.getInstance().getResourceManager().getResource(i).isPresent());
    }

    static void pic(DrawContext c, TextRenderer t, Identifier id, int x, int y, int w, int h) {
        if (has(id)) {
            c.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0, 0, w, h, w, h);
        } else {
            c.fill(x, y, x + w, y + h, 0xFF1E1E28);
            c.drawText(t, "no image", x + 6, y + h / 2 - 4, 0xFF777777, false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no vanilla blur
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        TextRenderer t = this.textRenderer;
        EzConfig k = EzConfig.I;
        int acc = EzConfig.accent();
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;

        c.fillGradient(0, 0, width, height, 0xC0000000, 0xA0000000);
        c.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, acc);
        c.fill(x0, y0, x0 + W, y0 + H, EzConfig.bg());
        c.fill(x0, y0, x0 + SIDE, y0 + H, EzConfig.side());

        // sidebar banner with the girl
        c.enableScissor(x0, y0, x0 + SIDE, y0 + 104);
        pic(c, t, GIRL, x0, y0 - 6, SIDE, (int) (SIDE * 504f / 262f));
        c.disableScissor();
        c.fillGradient(x0, y0 + 40, x0 + SIDE, y0 + 104, 0x00000000, EzConfig.side());
        c.drawText(t, "EzVisuals", x0 + 12, y0 + 72, acc, true);
        c.drawText(t, "Better. Prettier.", x0 + 12, y0 + 84, 0xFFBBBBBB, false);
        c.drawText(t, "Stronger.", x0 + 12, y0 + 94, 0xFFBBBBBB, false);

        // header
        c.fillGradient(x0 + SIDE, y0, x0 + W, y0 + 28, EzConfig.side(), EzConfig.bg());
        c.fill(x0 + SIDE, y0 + 27, x0 + W, y0 + 28, acc);
        MinecraftClient mc = MinecraftClient.getInstance();
        String info = "FPS " + mc.getCurrentFps() + "   TPS " + String.format(Locale.US, "%.1f", EzVisuals.tps);
        if (mc.player != null) info += "   XYZ " + (int) Math.floor(mc.player.getX()) + " " + (int) Math.floor(mc.player.getY()) + " " + (int) Math.floor(mc.player.getZ());
        c.drawText(t, info, x0 + W - t.getWidth(info) - 12, y0 + 10, 0xFFFFFFFF, true);
        c.drawText(t, TABS[tab], x0 + SIDE + 12, y0 + 10, acc, true);

        // tabs
        float target = tabY(tab);
        if (indY == 0) indY = target;
        indY += (target - indY) * 0.3f;
        c.fill(x0, (int) indY - 2, x0 + SIDE, (int) indY + 16, 0xFF252533);
        c.fill(x0, (int) indY - 2, x0 + 3, (int) indY + 16, acc);
        for (int i = 0; i < TABS.length; i++) {
            int ty = tabY(i);
            boolean h = in(mx, my, x0, ty - 2, SIDE, 18);
            c.drawText(t, TABS[i], x0 + 14, ty + 2, i == tab ? acc : h ? 0xFFFFFFFF : 0xFF888888, i == tab);
        }

        int cx = x0 + SIDE + 20, cy = y0 + 44;
        if (tab == 7) {
            c.drawText(t, "Custom Pictures  |  active slot: " + EzConfig.I.pictureSlot, cx, cy - 2, acc, true);
            for (int i = 0; i < 4; i++) {
                int px = x0 + SIDE + 14 + i * 114, py = cy + 14;
                pic(c, t, Identifier.of(EzVisuals.ID, "textures/gui/pic" + (i + 1) + ".png"), px, py, 106, 150);
                c.drawText(t, "#" + (i + 1), px + 48, py + 156, 0xFFDDDDDD, true);
            }
            c.drawText(t, "assets/ezvisuals/textures/gui/pic1..pic4.png", cx, y0 + H - 22, 0xFF666666, false);
            c.drawText(t, "Click a picture to select it", cx, cy + 184, 0xFFAAAAAA, false);
        } else {
            List<Row> rows = rows(tab);
            for (int i = 0; i < rows.size(); i++) rows.get(i).draw(c, t, cx, cy + i * RH, mx, my);
            pic(c, t, Identifier.of(EzVisuals.ID, "textures/gui/pic" + Math.max(1, Math.min(4, EzConfig.I.pictureSlot)) + ".png"), x0 + W - 150, y0 + H - 170, 140, 152);
        }
        super.render(c, mx, my, delta);
    }

    // ---------------- input ----------------
    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        listening = null;
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, x0, tabY(i) - 2, SIDE, 18)) { tab = i; return true; }
        }
        if (tab == 7) {
            int cy = y0 + 44;
            for (int i = 0; i < 4; i++) {
                int px = x0 + SIDE + 14 + i * 114, py = cy + 14;
                if (in(mx, my, px, py, 106, 150)) { EzConfig.I.pictureSlot = i + 1; EzConfig.save(0); return true; }
            }
        }
        if (tab != 7) {
            List<Row> rows = rows(tab);
            int cx = x0 + SIDE + 20, cy = y0 + 44;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.click(cx, cy + i * RH, mx, my)) {
                    if (r.draggable()) { dragging = r; dragX = cx; }
                    return true;
                }
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragging.drag(dragX, (int) mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) listening.setKey(-1);
            else if (key != GLFW.GLFW_KEY_ESCAPE) listening.setKey(key);
            listening = null;
            return true;
        }
        if (key == EzConfig.I.keyMenu || key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() { EzConfig.save(0); }

    @Override
    public boolean shouldPause() { return false; }
}

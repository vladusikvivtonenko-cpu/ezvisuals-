package com.ezvisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class EzConfig {
    public static EzConfig I = new EzConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // {accent, background, sidebar}
    public static final String[] THEME_NAMES = {"Blood Moon", "Cosmic Purple", "Cyber Cyan", "Emerald"};
    public static final int[][] THEMES = {
            {0xFFFF3158, 0xFF10070D, 0xFF24101A},
            {0xFF8A2BE2, 0xFF0C0814, 0xFF1A1030},
            {0xFF00E5FF, 0xFF06101A, 0xFF0E1E2C},
            {0xFF3DDC84, 0xFF07120C, 0xFF10241A}};

    // Visuals
    public int theme = 0;
    public boolean noFire = true, noShake = true, itemGlow = true;
    // HUD
    public boolean topBar = true, armorHud = true, potionHud = true, potionBeneficialOnly = false, potionBackground = true, cooldownsHud = true,
            totemHelper = true, nearbyPlayers = true, lookSlots = true, fullBridge = false, armorNameTags = true, swimAnimation = false;
    public int potionTimeFormat = 0; // 0 = MM:SS, 1 = seconds
    public float swimAmount = 0.5f;
    public int pictureSlot = 1;
    // FT helper / predictions
    public boolean ftHelper = true, predictions = true, pvpSafe = true;
    public float radDisorient = 10f, radPlast = 5f, radSnowball = 5f, radCrossbow = 3f, radPotion = 4f;
    // Effects
    public int weather = 0; // 0 off, 1 rain, 2 snow, 3 space
    public boolean bloodVisuals = false;
    public float weatherDensity = 100f;
    // Optimize
    public boolean reduceParticles = true, lowEnd = false;
    public float particleKeep = 40f; // percent
    // Binds (GLFW key code, -1 = none)
    public int keyMenu = GLFW.GLFW_KEY_RIGHT_SHIFT, keyZoom = GLFW.GLFW_KEY_C,
            keyHome = -1, keyArena = -1, keyHub = -1, keyBridge = GLFW.GLFW_KEY_B, keyLookSlots = GLFW.GLFW_KEY_L,
            keyTrap = -1, keyDisorient = -1, keyWeb = -1, keyGapple = -1, keyTotem = -1;
    public float zoomFactor = 0.25f;
    // Colors
    public boolean customAccent = false, customNameColor = false;
    public float accR = 255, accG = 49, accB = 88, nameR = 255, nameG = 255, nameB = 255;
    // CastomWorld (visual layer)
    public boolean cwEnabled = false;
    public int cwPreset = 1, cwHero = 0, cwHeroPos = 0;
    public float cwStrength = 0.15f, cwVignette = 0.35f, cwHeroAlpha = 0.35f, cwHeroSize = 0.45f;
    // Lua scripts: file name -> enabled
    public Map<String, Boolean> scripts = new HashMap<>();

    static int ch(float v) { return Math.max(0, Math.min(255, (int) v)); }
    static int rgb(float r, float g, float b) { return (ch(r) << 16) | (ch(g) << 8) | ch(b); }

    public static int accent() {
        if (I.customAccent) return 0xFF000000 | rgb(I.accR, I.accG, I.accB);
        return THEMES[Math.floorMod(I.theme, THEMES.length)][0];
    }
    public static int bg() {
        if (I.customAccent) return 0xFF000000 | rgb(I.accR * 0.07f, I.accG * 0.07f, I.accB * 0.07f);
        return THEMES[Math.floorMod(I.theme, THEMES.length)][1];
    }
    public static int side() {
        if (I.customAccent) return 0xFF000000 | rgb(I.accR * 0.14f, I.accG * 0.14f, I.accB * 0.14f);
        return THEMES[Math.floorMod(I.theme, THEMES.length)][2];
    }
    public static int nameColor() {
        return I.customNameColor ? 0xFF000000 | rgb(I.nameR, I.nameG, I.nameB) : accent();
    }

    private static Path path(int slot) {
        return FabricLoader.getInstance().getConfigDir().resolve(slot == 0 ? "ezvisuals.json" : "ezvisuals_" + slot + ".json");
    }

    public static void save(int slot) {
        try { Files.writeString(path(slot), GSON.toJson(I)); } catch (Exception ignored) {}
    }

    public static boolean load(int slot) {
        try {
            Path p = path(slot);
            if (!Files.exists(p)) return false;
            EzConfig c = GSON.fromJson(Files.readString(p), EzConfig.class);
            if (c != null) { I = c; return true; }
        } catch (Exception ignored) {}
        return false;
    }
}

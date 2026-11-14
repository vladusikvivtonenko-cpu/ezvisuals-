package com.ezvisuals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Sandboxed Lua scripting (no io / os / java access). Scripts live in config/ezvisuals/scripts/*.lua */
public class EzLua {
    public static class Script {
        public final String name;
        Globals g;
        LuaValue onTick = LuaValue.NIL, onHud = LuaValue.NIL;
        public String error;
        Script(Path f) { name = f.getFileName().toString(); }
    }

    public static final List<Script> scripts = new ArrayList<>();
    private static DrawContext ctx;
    private static TextRenderer tr;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("ezvisuals").resolve("scripts");
    }

    public static boolean enabled(Script s) { return EzConfig.I.scripts.getOrDefault(s.name, true); }
    public static void setEnabled(Script s, boolean v) { EzConfig.I.scripts.put(s.name, v); }

    public static void init() { reload(); }

    public static void reload() {
        scripts.clear();
        try {
            Files.createDirectories(dir());
            List<Path> files;
            try (var st = Files.list(dir())) {
                files = st.filter(p -> p.toString().endsWith(".lua")).sorted().toList();
            }
            if (files.isEmpty()) {
                Path ex = dir().resolve("example.lua");
                Files.writeString(ex, EXAMPLE);
                files = List.of(ex);
            }
            for (Path p : files) scripts.add(load(p));
        } catch (Throwable ignored) {}
    }

    private static Script load(Path p) {
        Script s = new Script(p);
        try {
            s.g = newGlobals();
            s.g.set("ez", api());
            s.g.load(Files.readString(p), s.name).call();
            s.onTick = s.g.get("onTick");
            s.onHud = s.g.get("onHud");
        } catch (Throwable e) {
            s.error = msg(e);
        }
        return s;
    }

    private static String msg(Throwable e) {
        String m = String.valueOf(e.getMessage()).replace('\n', ' ').replace('\r', ' ');
        return m.length() > 80 ? m.substring(0, 80) : m;
    }

    private static Globals newGlobals() {
        Globals g = new Globals();
        g.load(new JseBaseLib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new Bit32Lib());
        g.load(new JseMathLib());
        LoadState.install(g);
        LuaC.install(g);
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        return g;
    }

    public static void tick() {
        for (Script s : scripts) {
            if (enabled(s) && s.error == null && s.onTick.isfunction()) run(s, s.onTick);
        }
    }

    public static void hud(DrawContext c, TextRenderer t) {
        ctx = c;
        tr = t;
        try {
            for (Script s : scripts) {
                if (enabled(s) && s.error == null && s.onHud.isfunction()) run(s, s.onHud);
            }
        } finally {
            ctx = null;
            tr = null;
        }
    }

    private static void run(Script s, LuaValue f) {
        try {
            f.call();
        } catch (Throwable e) {
            s.error = msg(e);
            EzVisuals.toast("Lua error in " + s.name);
        }
    }

    // ---------------- API exposed to scripts as `ez` ----------------
    private static LuaTable api() {
        LuaTable ez = new LuaTable();

        ez.set("toast", new OneArgFunction() {
            public LuaValue call(LuaValue a) { EzVisuals.toast(a.tojstring()); return LuaValue.NIL; }
        });
        ez.set("fps", new ZeroArgFunction() {
            public LuaValue call() { return LuaValue.valueOf(MinecraftClient.getInstance().getCurrentFps()); }
        });
        ez.set("tps", new ZeroArgFunction() {
            public LuaValue call() { return LuaValue.valueOf((double) EzVisuals.tps); }
        });
        ez.set("accent", new ZeroArgFunction() {
            public LuaValue call() { return LuaValue.valueOf(EzConfig.accent()); }
        });
        ez.set("screen", new ZeroArgFunction() {
            public Varargs invoke() {
                MinecraftClient mc = MinecraftClient.getInstance();
                return LuaValue.varargsOf(LuaValue.valueOf(mc.getWindow().getScaledWidth()),
                        LuaValue.valueOf(mc.getWindow().getScaledHeight()));
            }
        });
        ez.set("color", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                int r = a.optint(1, 255), g = a.optint(2, 255), b = a.optint(3, 255), al = a.optint(4, 255);
                return LuaValue.valueOf((al << 24) | (r << 16) | (g << 8) | b);
            }
        });
        ez.set("player", new ZeroArgFunction() {
            public LuaValue call() {
                ClientPlayerEntity p = MinecraftClient.getInstance().player;
                if (p == null) return LuaValue.NIL;
                LuaTable t = new LuaTable();
                t.set("name", p.getName().getString());
                t.set("x", p.getX());
                t.set("y", p.getY());
                t.set("z", p.getZ());
                t.set("health", p.getHealth());
                t.set("maxHealth", p.getMaxHealth());
                t.set("food", p.getHungerManager().getFoodLevel());
                t.set("yaw", p.getYaw());
                t.set("pitch", p.getPitch());
                t.set("onGround", LuaValue.valueOf(p.isOnGround()));
                t.set("swimming", LuaValue.valueOf(p.isSwimming()));
                return t;
            }
        });
        // drawing (only works inside onHud)
        ez.set("rect", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                if (ctx == null) return LuaValue.NONE;
                int x = a.toint(1), y = a.toint(2), w = a.toint(3), h = a.toint(4);
                int col = (int) a.tolong(5);
                ctx.fill(x, y, x + w, y + h, col);
                return LuaValue.NONE;
            }
        });
        ez.set("text", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                if (ctx == null || tr == null) return LuaValue.NONE;
                int col = a.narg() >= 4 ? (int) a.tolong(4) : 0xFFFFFFFF;
                ctx.drawText(tr, a.tojstring(1), a.toint(2), a.toint(3), col, true);
                return LuaValue.NONE;
            }
        });
        ez.set("textWidth", new OneArgFunction() {
            public LuaValue call(LuaValue a) {
                return LuaValue.valueOf(MinecraftClient.getInstance().textRenderer.getWidth(a.tojstring()));
            }
        });
        // read / write mod settings, e.g. ez.set("noFire", true)
        ez.set("get", new OneArgFunction() {
            public LuaValue call(LuaValue n) {
                try {
                    Field f = EzConfig.class.getField(n.tojstring());
                    if (Modifier.isStatic(f.getModifiers())) return LuaValue.NIL;
                    Object o = f.get(EzConfig.I);
                    if (o instanceof Boolean b) return LuaValue.valueOf(b);
                    if (o instanceof Number x) return LuaValue.valueOf(x.doubleValue());
                } catch (Throwable ignored) {}
                return LuaValue.NIL;
            }
        });
        ez.set("set", new TwoArgFunction() {
            public LuaValue call(LuaValue n, LuaValue v) {
                try {
                    Field f = EzConfig.class.getField(n.tojstring());
                    if (Modifier.isStatic(f.getModifiers())) return LuaValue.NIL;
                    Class<?> t = f.getType();
                    if (t == boolean.class) f.setBoolean(EzConfig.I, v.toboolean());
                    else if (t == int.class) f.setInt(EzConfig.I, v.toint());
                    else if (t == float.class) f.setFloat(EzConfig.I, (float) v.todouble());
                } catch (Throwable ignored) {}
                return LuaValue.NIL;
            }
        });
        return ez;
    }

    private static final String EXAMPLE = """
            -- EzVisuals Lua script (edit me, then press "Reload scripts" in the menu)
            --
            -- Hooks:   onTick()  every client tick
            --          onHud()   every frame (drawing works only here)
            -- API:     ez.toast(msg)               ez.fps()  ez.tps()
            --          ez.screen() -> w, h         ez.player() -> table or nil
            --          ez.rect(x, y, w, h, color)  ez.text(str, x, y [, color])
            --          ez.color(r, g, b [, a])     ez.accent()  ez.textWidth(str)
            --          ez.get("noFire")            ez.set("noFire", true)
            -- Any setting from EzConfig can be read/changed with ez.get / ez.set.

            function onHud()
              local p = ez.player()
              if not p then return end
              local w, h = ez.screen()
              local s = string.format("Lua | HP %.0f | Y %.0f", p.health, p.y)
              local tw = ez.textWidth(s)
              ez.rect(6, h - 24, tw + 10, 14, ez.color(16, 16, 24, 170))
              ez.rect(6, h - 24, tw + 10, 2, ez.accent())
              ez.text(s, 11, h - 20, ez.color(255, 255, 255))
            end
            """;
}

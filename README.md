# EzVisuals (Fabric, Minecraft 1.21.4)

Build: push to GitHub -> Actions -> build -> Artifacts -> ezvisuals-jar (one jar, no sources).
Put it in mods/ together with Fabric API 1.21.4. Menu key: Right Shift (changeable in Binds).

## Lua scripts
Folder: .minecraft/config/ezvisuals/scripts/*.lua (an example.lua is created on first start).
Menu -> Scripts -> Reload scripts.

Hooks: onTick(), onHud()
API:   ez.toast(msg)  ez.fps()  ez.tps()  ez.screen() -> w,h  ez.player() -> table|nil
       ez.rect(x,y,w,h,color)  ez.text(str,x,y[,color])  ez.textWidth(str)
       ez.color(r,g,b[,a])  ez.accent()  ez.get("setting")  ez.set("setting", value)
Scripts are sandboxed: no io, os or Java access.

## CastomWorld
Menu -> CastomWorld: color tint, vignette and a hero picture overlay (pic1-4 / girl).
This is a screen overlay, not real shaders. For real shader packs install Iris + a shader pack.

Images: src/main/resources/assets/ezvisuals/textures/gui/ (girl.png, pic1..pic4.png).

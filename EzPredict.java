package com.ezvisuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** Visual only: draws predicted flight path + radius circle. Does not touch any gameplay. */
public class EzPredict {

    public static void render(WorldRenderContext ctx) {
        EzConfig k = EzConfig.I;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || ctx.matrixStack() == null || ctx.consumers() == null) return;
        if (!k.predictions && !k.ftHelper) return;

        ClientPlayerEntity p = mc.player;
        ItemStack s = p.getMainHandStack();
        if (s.isEmpty()) return;
        Item it = s.getItem();
        String name = s.getName().getString().toLowerCase();

        double speed = 0, grav = 0.03;
        float radius = 0;
        boolean throwable = true;

        if (it == Items.BOW && p.isUsingItem()) {
            float f = BowItem.getPullProgress(p.getItemUseTime());
            if (f < 0.1f) return;
            speed = f * 3.0; grav = 0.05;
        } else if (it == Items.CROSSBOW && CrossbowItem.isCharged(s)) {
            speed = 3.15; grav = 0.05; radius = k.radCrossbow;
        } else if (it == Items.TRIDENT && p.isUsingItem()) {
            speed = 2.5; grav = 0.05;
        } else if (it == Items.SNOWBALL || it == Items.EGG) {
            speed = 1.5; radius = it == Items.SNOWBALL ? k.radSnowball : 0;
        } else if (it == Items.ENDER_PEARL) {
            speed = 1.5;
        } else if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) {
            speed = 0.5; grav = 0.05; radius = k.radPotion;
        } else {
            throwable = false;
        }

        // Custom server items recognised by name (placeholder keywords, adjust to your server)
        float nameRadius = 0;
        if (name.contains("дезор")) nameRadius = k.radDisorient;
        else if (name.contains("пласт")) nameRadius = k.radPlast;
        if (nameRadius > 0 && k.ftHelper) radius = nameRadius;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry e = ms.peek();
        int acc = EzConfig.accent();

        if (throwable && speed > 0 && k.predictions) {
            Vec3d pos = p.getEyePos().subtract(0, 0.1, 0);
            Vec3d vel = p.getRotationVec(1f).multiply(speed);
            Vec3d pv = p.getVelocity();
            vel = vel.add(pv.x, p.isOnGround() ? 0 : pv.y, pv.z);
            List<Vec3d> pts = new ArrayList<>();
            Vec3d end = null;
            for (int i = 0; i < 200; i++) {
                Vec3d next = pos.add(vel);
                BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
                pts.add(pos);
                if (hit.getType() != HitResult.Type.MISS) { end = hit.getPos(); pts.add(end); break; }
                pos = next;
                vel = vel.multiply(0.99).add(0, -grav, 0);
                if (pos.y < mc.world.getBottomY()) break;
            }
            for (int i = 0; i + 1 < pts.size(); i++) line(vc, e, pts.get(i), pts.get(i + 1), acc);
            if (end != null) {
                circle(vc, e, end.add(0, 0.05, 0), 0.4, acc);
                if (radius > 0 && k.ftHelper) circle(vc, e, end.add(0, 0.05, 0), radius, 0xFFFF4D5E);
            }
        } else if (radius > 0 && k.ftHelper && nameRadius > 0) {
            // non-throwable custom item: show radius around the player
            circle(vc, e, p.getPos().add(0, 0.05, 0), radius, 0xFFFF4D5E);
        }

        if (vcp instanceof VertexConsumerProvider.Immediate im) im.draw(RenderLayer.getLines());
        ms.pop();
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry e, Vec3d a, Vec3d b, int col) {
        Vec3d d = b.subtract(a);
        if (d.lengthSquared() < 1e-8) return;
        d = d.normalize();
        vc.vertex(e, (float) a.x, (float) a.y, (float) a.z).color(col).normal(e, (float) d.x, (float) d.y, (float) d.z);
        vc.vertex(e, (float) b.x, (float) b.y, (float) b.z).color(col).normal(e, (float) d.x, (float) d.y, (float) d.z);
    }

    private static void circle(VertexConsumer vc, MatrixStack.Entry e, Vec3d c, double r, int col) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            line(vc, e, c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r), c.add(Math.cos(a2) * r, 0, Math.sin(a2) * r), col);
        }
    }
}

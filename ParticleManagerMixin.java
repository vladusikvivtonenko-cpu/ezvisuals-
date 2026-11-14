package com.ezvisuals.mixin;

import com.ezvisuals.EzConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void ez$limit(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                          CallbackInfoReturnable<Particle> cir) {
        EzConfig k = EzConfig.I;
        if (!k.reduceParticles && !k.lowEnd) return;
        int keep = k.lowEnd ? Math.min(20, (int) k.particleKeep) : (int) k.particleKeep;
        if (ThreadLocalRandom.current().nextInt(100) >= keep) cir.setReturnValue(null);
    }
}

package moe.paring.createlogisticsbackport.registry;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.particle.ICustomParticleData;
import com.simibubi.create.foundation.utility.Lang;
import moe.paring.createlogisticsbackport.content.logistics.packagerLink.WiFiParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public enum ExtraParticleTypes {
    WIFI(WiFiParticle.Data::new),

    ;

    private final ParticleEntry<?> entry;

    <D extends ParticleOptions> ExtraParticleTypes(Supplier<? extends ICustomParticleData<D>> typeFactory) {
        String name = Lang.asId(name());
        entry = new ParticleEntry<>(name, typeFactory);
    }

    public static void register(IEventBus modEventBus) {
        ParticleEntry.REGISTER.register(modEventBus);
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerFactories(RegisterParticleProvidersEvent event) {
        for (ExtraParticleTypes particle : values())
            particle.entry.registerFactory(event);
    }

    public ParticleType<?> get() {
        return entry.object.get();
    }

    public String parameter() {
        return entry.name;
    }

    private static class ParticleEntry<D extends ParticleOptions> {
        private static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Create.ID);

        private final String name;
        private final Supplier<? extends ICustomParticleData<D>> typeFactory;
        private final RegistryObject<ParticleType<D>> object;

        public ParticleEntry(String name, Supplier<? extends ICustomParticleData<D>> typeFactory) {
            this.name = name;
            this.typeFactory = typeFactory;

            object = REGISTER.register(name, () -> this.typeFactory.get().createType());
        }

        @OnlyIn(Dist.CLIENT)
        public void registerFactory(RegisterParticleProvidersEvent event) {
            typeFactory.get()
                    .register(object.get(), event);
        }

    }
}

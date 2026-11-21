package com.xiong.petrification.forge;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.platform.MenuFactory;
import com.xiong.petrification.platform.ModPlatform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.api.distmarker.Dist;

import java.util.function.Supplier;

/** Forge 侧的 Platform 实现：DeferredRegister + RegistryObject + IForgeMenuType。 */
public final class ForgePlatform implements ModPlatform {

    private final BusGroup modBus;

    public ForgePlatform(BusGroup modBus) {
        this.modBus = modBus;
    }

    public BusGroup modBus() {
        return modBus;
    }

    @Override
    public String name() {
        return "forge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public <T> Registrar<T> createRegistrar(ResourceKey<? extends Registry<T>> registry) {
        return new ForgeRegistrar<>(DeferredRegister.create(registry, PetrificationMod.MODID));
    }

    @Override
    public <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }

    private final class ForgeRegistrar<T> implements Registrar<T> {
        private final DeferredRegister<T> delegate;

        private ForgeRegistrar(DeferredRegister<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <R extends T> RegistryEntry<R> register(String name, Supplier<R> factory) {
            return new ForgeEntry<>(delegate.register(name, factory));
        }

        @Override
        public void bind() {
            delegate.register(modBus);
        }

        @Override
        public ResourceKey<? extends Registry<T>> registryKey() {
            return delegate.getRegistryKey();
        }
    }

    private record ForgeEntry<T>(RegistryObject<T> object) implements RegistryEntry<T> {
        @Override
        public T get() {
            return object.get();
        }

        @Override
        public Identifier id() {
            return object.getId();
        }

        @Override
        public Holder<T> holder() {
            return object.getHolder().orElseThrow(() -> new IllegalStateException("Entry not bound: " + object.getId()));
        }

        @Override
        public boolean isPresent() {
            return object.isPresent();
        }
    }
}

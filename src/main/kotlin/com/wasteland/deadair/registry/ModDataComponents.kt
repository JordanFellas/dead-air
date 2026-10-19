package com.wasteland.deadair.registry

import com.mojang.serialization.Codec
import com.wasteland.deadair.DeadAir
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModDataComponents {
    val DATA_COMPONENTS: DeferredRegister<DataComponentType<*>> =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DeadAir.MOD_ID)

    val FREQUENCY: DeferredHolder<DataComponentType<*>, DataComponentType<Float>> =
        DATA_COMPONENTS.register("frequency", Supplier {
            DataComponentType.builder<Float>().persistent(Codec.FLOAT).build()
        })

    val POWER: DeferredHolder<DataComponentType<*>, DataComponentType<Boolean>> =
        DATA_COMPONENTS.register("power", Supplier {
            DataComponentType.builder<Boolean>().persistent(Codec.BOOL).build()
        })

    val ENERGY: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        DATA_COMPONENTS.register("energy", Supplier {
            DataComponentType.builder<Int>().persistent(Codec.INT).build()
        })

    val VOLUME: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        DATA_COMPONENTS.register("volume", Supplier {
            DataComponentType.builder<Int>().persistent(Codec.INT).build()
        })
}

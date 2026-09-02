package dev.yuzhe.aeprimitives.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Read-only inspection boundary for optional factory resource ports. */
public interface FactoryResourcePort {
    @Nullable BlockPos lensOwner();

    ResourceLocation lensResourceId();
}

package com.helium.mixin.compat;

import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface GameOptionsAccessor {
    
    @Accessor("keyMappings")
    KeyMapping[] helium$getallkeys();
    
    @Mutable
    @Accessor("keyMappings")
    void helium$setallkeys(KeyMapping[] keys);
}

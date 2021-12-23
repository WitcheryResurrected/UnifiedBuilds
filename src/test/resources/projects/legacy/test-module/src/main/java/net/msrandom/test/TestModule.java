package net.msrandom.test;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "test-module", useMetadata = true)
public class TestModule {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("Printing from Test Module");
    }
}

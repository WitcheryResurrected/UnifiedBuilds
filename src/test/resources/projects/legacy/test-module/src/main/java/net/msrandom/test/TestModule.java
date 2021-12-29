package net.msrandom.test;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "test-module", useMetadata = true)
public class TestModule {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.printf("Printing from Test Module, with message from Test Base: %s\n", TestBase.someImportantMessage());
    }
}

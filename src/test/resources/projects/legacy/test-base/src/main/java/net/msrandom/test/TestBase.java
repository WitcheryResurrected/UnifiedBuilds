package net.msrandom.test;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "test-base", useMetadata = true)
public class TestBase {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("Printing from Test Base");
    }
}

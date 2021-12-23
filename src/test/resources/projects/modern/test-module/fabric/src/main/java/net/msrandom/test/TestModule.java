package net.msrandom.test;

import net.fabricmc.api.ModInitializer;
import net.msrandom.test.TestModuleCommon;

public class TestModule implements ModInitializer {
    @Override
    public void onInitialize() {
        TestModuleCommon.printStuff();
    }
}

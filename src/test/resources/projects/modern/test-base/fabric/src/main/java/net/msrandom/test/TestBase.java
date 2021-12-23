package net.msrandom.test;

import net.fabricmc.api.ModInitializer;
import net.msrandom.test.TestBaseCommon;

public class TestBase implements ModInitializer {
    @Override
    public void onInitialize() {
        TestBaseCommon.printStuff();
    }
}

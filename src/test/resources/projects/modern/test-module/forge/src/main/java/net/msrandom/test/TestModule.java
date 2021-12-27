package net.msrandom.test;

import net.minecraftforge.fml.common.Mod;
import net.msrandom.test.TestModuleCommon;

@Mod("test-module")
public class TestModule {
    public TestModule() {
        TestModuleCommon.printStuff();
    }
}

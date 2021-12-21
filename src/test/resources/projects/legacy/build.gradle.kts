plugins {
    id("net.msrandom.unifiedbuilds") version "1.+"
}

/*
allprojects {
    apply<UnifiedBuildsPlugin>()

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    unifiedModule {
        platforms {
            add(Platform("forge", this@allprojects, PlatformType.FORGE, "14.23.5.2855"))
        }
    }
}

unifiedBuilds {
    minecraftVersion = "1.12.2"

    baseProject.set(project(":test-base"))
    modules.addAll(subprojects)
}
*/

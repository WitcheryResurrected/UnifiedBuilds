import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.platforms.Legacy
import org.gradle.api.JavaVersion

plugins {
    id("unifiedbuilds")
}

base {
    archivesName.set("TestMod")
}

allprojects {
    apply<UnifiedBuildsPlugin>()

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    unifiedModule {
        platforms {
            add(Legacy("forge", "14.23.5.2855"))
        }
    }
}

unifiedBuilds {
    minecraftVersion.set("1.12.2")
    license.set("CC-2.0")
    modVersion.set("1.0")

    baseProject.set(project(":test-base"))
    modules.addAll(childProjects.values)
}

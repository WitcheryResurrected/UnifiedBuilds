import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.platforms.Forge
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPlugin

plugins {
    id("net.msrandom.unifiedbuilds") version "1.+"
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
            add(Forge("forge", "14.23.5.2855"))
        }
    }
}

unifiedBuilds {
    minecraftVersion.set("1.16.5")
    license.set("CC-2.0")

    baseProject.set(project(":test-base"))
    modules.addAll(childProjects.values)
}

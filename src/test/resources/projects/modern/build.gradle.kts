import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.platforms.fabric.Fabric
import net.msrandom.unifiedbuilds.platforms.Forge
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPlugin

plugins {
    id("net.msrandom.unifiedbuilds") version "1.+"
}

base {
    archivesName.set("TestMod")
}

val subModules = childProjects.values.filter { it.name != "forge" && it.name != "fabric" }

allprojects {
    apply<UnifiedBuildsPlugin>()

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

(subModules + project).forEach {
    it.run {
        // This version string will be used everywhere, whether you specify this per module or globally like this is a matter of preference.
        version = "1.0"

        unifiedModule {
            common = "common"
            platforms {
                add(Forge("forge", "36.2.19"))
                add(Fabric("fabric", "0.12.10", "0.42.0+1.16"))
            }
        }
    }
}

// Root project specific setup.
unifiedBuilds {
    minecraftVersion.set("1.16.5")
    license.set("CC-2.0")

    baseProject.set(project(":test-base"))
    modules.addAll(subModules)
}

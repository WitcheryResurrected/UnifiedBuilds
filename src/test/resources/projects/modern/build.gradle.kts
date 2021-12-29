import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.platforms.Fabric
import net.msrandom.unifiedbuilds.platforms.Forge
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("unifiedbuilds") version "1.+"
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
        unifiedModule {
            common.set("common")

            platforms {
                add(Forge("forge", "36.2.19"))
                add(Fabric("fabric", "0.12.10", "0.42.0+1.16"))
            }
        }
    }
}

unifiedBuilds {
    minecraftVersion.set("1.16.5")
    license.set("CC-2.0")
    modVersion.set("1.0")

    baseProject.set(project(":test-base"))
    modules.addAll(subModules)
}

import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.platforms.Forge
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPlugin

plugins {
    id("unifiedbuilds") version "1.+"
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
    sourceDirectoryHandler.set { sourceSets.main.flatMap { it.java.destinationDirectory } }
    minecraftVersion.set("1.12.2")
    license.set("CC-2.0")
    modVersion.set("1.0")

    baseProject.set(project(":test-base"))
    modules.addAll(childProjects.values)
}

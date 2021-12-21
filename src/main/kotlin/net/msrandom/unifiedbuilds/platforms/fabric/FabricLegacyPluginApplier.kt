package net.msrandom.unifiedbuilds.platforms.fabric

import net.fabricmc.loom.LoomGradlePlugin
import org.gradle.api.Project

object FabricPluginApplier {
    operator fun invoke(project: Project) {
        project.apply {
            it.plugin(LoomGradlePlugin::class.java)
        }
    }
}

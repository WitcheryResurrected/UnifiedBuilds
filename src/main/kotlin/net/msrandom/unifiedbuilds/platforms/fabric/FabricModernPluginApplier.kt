package net.msrandom.unifiedbuilds.platforms.fabric

import org.gradle.api.Project
import net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap

object FabricPluginApplier {
    operator fun invoke(project: Project) {
        project.apply {
            it.plugin(LoomGradlePluginBootstrap::class.java)
        }
    }
}

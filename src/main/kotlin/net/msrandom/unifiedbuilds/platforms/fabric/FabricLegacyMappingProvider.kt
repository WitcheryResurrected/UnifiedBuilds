package net.msrandom.unifiedbuilds.platforms.fabric

import net.fabricmc.loom.LoomGradleExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

object FabricMappingProvider {
    fun getDependency(project: Project): Dependency =
        project.extensions.getByType(LoomGradleExtension::class.java).officialMojangMappings()

    fun disableRemaps(project: Project) {
        project.extensions.getByType(LoomGradleExtension::class.java).remapMod = false
    }
}

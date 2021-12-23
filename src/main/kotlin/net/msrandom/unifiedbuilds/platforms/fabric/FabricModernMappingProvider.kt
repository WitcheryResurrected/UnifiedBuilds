package net.msrandom.unifiedbuilds.platforms.fabric

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

object FabricMappingProvider {
    fun getDependency(project: Project): Dependency =
        project.extensions.getByType(LoomGradleExtensionAPI::class.java).officialMojangMappings()

    fun disableRemaps(project: Project) {
        project.extensions.getByType(LoomGradleExtensionAPI::class.java).remapArchives.set(false)
    }
}

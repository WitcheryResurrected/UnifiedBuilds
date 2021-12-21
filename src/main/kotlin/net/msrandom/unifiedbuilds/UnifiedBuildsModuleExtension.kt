package net.msrandom.unifiedbuilds

import net.msrandom.unifiedbuilds.platforms.Platform
import org.gradle.api.Project

abstract class UnifiedBuildsModuleExtension(val project: Project) {
    var common: String? = null
    val platforms = project.container(Platform::class.java)

    val info = ModInformation(project)
}

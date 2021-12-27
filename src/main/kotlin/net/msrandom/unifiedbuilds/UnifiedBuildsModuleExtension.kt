package net.msrandom.unifiedbuilds

import net.msrandom.unifiedbuilds.platforms.Platform
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class UnifiedBuildsModuleExtension(val project: Project) {
    abstract val common: Property<String>

    val platforms: NamedDomainObjectContainer<Platform> = project.container(Platform::class.java)
    val info = ModInformation(project)

    internal var named = false
}

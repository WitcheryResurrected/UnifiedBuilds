package net.msrandom.unifiedbuilds

import net.msrandom.unifiedbuilds.platforms.ProjectPlatform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class UnifiedBuildsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.run {
            val unifiedBuilds = extensions.create("unifiedBuilds", UnifiedBuildsExtension::class.java, project)
            val unifiedModule = extensions.create("unifiedModule", UnifiedBuildsModuleExtension::class.java, project)
            apply {
                it.plugin(JavaPlugin::class.java)
            }

            unifiedBuilds.minecraftVersion.onSet { mcVersion ->
                unifiedBuilds.baseProject.onSet { baseProject ->
                    // If we have a version and base project, then this is the 'root' project.
                    unifiedModule.platforms.all { rootPlatform ->
                        val baseData = baseProject.extensions.getByType(UnifiedBuildsModuleExtension::class.java)
                        val base = baseData.platforms.firstOrNull { it.name == rootPlatform.name }
                        val baseProjectPlatform = base?.let { ProjectPlatform(baseData.project.childProjects[it.name] ?: baseData.project, it) }
                        rootPlatform.handle(mcVersion, childProjects[rootPlatform.name] ?: this, this, unifiedModule, baseProjectPlatform, null)
                        unifiedBuilds.modules.all { module ->
                            val data = module.extensions.getByType(UnifiedBuildsModuleExtension::class.java)
                            // Platforms in the core module that have the same name are considered the 'parent', this is the project you'd run for all modules to be available.
                            data.platforms.matching { it.name == rootPlatform.name }.all { platform ->
                                if (platform != rootPlatform) {
                                    val currentProject = module.childProjects[platform.name] ?: module
                                    val parentProject = childProjects[rootPlatform.name] ?: this
                                    if (base == platform) {
                                        platform.handle(mcVersion, currentProject, this, baseData, null, rootPlatform)
                                    } else {
                                        platform.handle(mcVersion, currentProject, this, data, baseProjectPlatform, rootPlatform)
                                    }
                                    parentProject.tasks.all {
                                        currentProject.tasks.findByName(it.name)?.let { task -> it.dependsOn(task) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

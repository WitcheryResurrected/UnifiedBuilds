package net.msrandom.unifiedbuilds

import net.msrandom.unifiedbuilds.platforms.ProjectPlatform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class UnifiedBuildsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val unifiedBuilds = project.extensions.create("unifiedBuilds", UnifiedBuildsExtension::class.java, project)
        val unifiedModule = project.extensions.create("unifiedModule", UnifiedBuildsModuleExtension::class.java, project)
        project.apply {
            it.plugin(JavaPlugin::class.java)
        }

        unifiedBuilds.minecraftVersion.onSet { mcVersion ->
            unifiedBuilds.baseProject.onSet { baseProject ->
                // If we have a version and base project, then this is the 'root' project.
                project.setupRootProject(mcVersion, unifiedBuilds, unifiedModule, baseProject)
            }
        }
    }

    private fun Project.setupRootProject(version: String, unifiedBuilds: UnifiedBuildsExtension, unifiedModule: UnifiedBuildsModuleExtension, baseProject: Project) {
        unifiedModule.platforms.all { rootPlatform ->
            val baseData = baseProject.extensions.getByType(UnifiedBuildsModuleExtension::class.java)
            val base = baseData.platforms.firstOrNull { it.name == rootPlatform.name }
            val baseProjectPlatform = base?.let { ProjectPlatform(it.getProject(baseData.project), it) }
            val parentProject = rootPlatform.getProject(this)
            if (parentProject != this) {
                tasks.named(JavaPlugin.JAR_TASK_NAME) {
                    it.enabled = false
                }
            }

            rootPlatform.handle(version, parentProject, this, unifiedModule, baseProjectPlatform, null)
            unifiedBuilds.modules.all { module ->
                val data = module.extensions.getByType(UnifiedBuildsModuleExtension::class.java)
                // Platforms in the core module that have the same name are considered the 'parent', this is the project you'd run for all modules to be available.
                data.platforms.matching { it.name == rootPlatform.name }.all { platform ->
                    if (platform != rootPlatform) {
                        val currentProject = platform.getProject(module)
                        if (currentProject != module) {
                            module.tasks.named(JavaPlugin.JAR_TASK_NAME) {
                                it.enabled = false
                            }
                        }

                        if (base == platform) {
                            platform.handle(version, currentProject, this, baseData, null, rootPlatform)
                        } else {
                            platform.handle(version, currentProject, this, data, baseProjectPlatform, rootPlatform)
                        }

                        gradle.projectsEvaluated {
                            parentProject.tasks.all {
                                currentProject.tasks.matching { task -> task.name == it.name }.all { task ->
                                    it.dependsOn(task)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

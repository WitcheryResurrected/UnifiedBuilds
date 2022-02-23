package net.msrandom.unifiedbuilds.platforms

import net.fabricmc.loom.util.Constants
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin.Companion.MODULE_DEP_CONFIGURATION_NAME
import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin.Companion.MOD_MODULE_CONFIGURATION_NAME
import net.msrandom.unifiedbuilds.tasks.AbstractModInfoTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import kotlin.reflect.KClass

abstract class Platform(val name: String, val loaderVersion: String) {
    abstract val remapTaskType: Class<out Jar>
    abstract val Jar.input: RegularFileProperty
    abstract val Jar.shade: ConfigurableFileCollection

    protected abstract val modInfo: ModInfoData

    private fun Project.depend(configuration: String, dependency: Any) {
        (dependencies.add(configuration, dependency) as ProjectDependency).isTransitive = false
    }

    protected fun UnifiedBuildsModuleExtension.dependencies(action: Project.() -> Unit) {
        project.configurations.getByName(MOD_MODULE_CONFIGURATION_NAME).dependencies.all {
            if (it is ProjectDependency) {
                val project = it.dependencyProject.childProjects[name] ?: it.dependencyProject
                project.action()
            }
        }
    }

    protected fun Project.moduleParent() = extensions.getByType(UnifiedBuildsModuleExtension::class.java).let {
        if (it.platforms.isEmpty()) parent else null
    }

    internal open fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME) { sourceSet ->
            if (module.common.isPresent) {
                val common = module.project.layout.projectDirectory.dir(module.common.get())
                sourceSet.java.srcDir(common.dir("java"))

                project.plugins.withId("kotlin") {
                    sourceSet.java.srcDir(common.dir("kotlin"))
                }

                project.plugins.withId("groovy") {
                    val groovy = sourceSet.extensions.getByName("groovy") as SourceDirectorySet
                    groovy.srcDir(common.dir("groovy"))
                }

                project.plugins.withId("scala") {
                    val scala = sourceSet.extensions.getByName("scala") as SourceDirectorySet
                    scala.srcDir(common.dir("scala"))
                }

                sourceSet.resources.srcDir(common.dir("resources"))
            }
        }

        if (module.project != project) {
            module.project.tasks.named(JavaPlugin.JAR_TASK_NAME).configure {
                it.enabled = false
            }
        }

        val modVersion = root.extensions.getByType(UnifiedBuildsExtension::class.java).modVersion.get()
        if (!module.named && module.project != project) {
            module.project.applyModuleNaming(version, modVersion, "", root, module)
            module.named = true
        }

        project.applyModuleNaming(version, modVersion, "-$name", root, module)

        project.tasks.withType(Jar::class.java).matching { !remapTaskType.isInstance(it) }.all { jar ->
            jar.archiveClassifier.convention("dev")

            // We want to include licenses by default, mods using this plugin can manually exclude them if needed
            jar.from(root.layout.projectDirectory.file("LICENSE")) {
                it.rename { name ->
                    val licenseType = root.extensions.getByType(UnifiedBuildsExtension::class.java).license.get()
                    val modName = project.extensions.getByType(BasePluginExtension::class.java).archivesName.get()
                    "${name}_${licenseType}_$modName"
                }
            }
        }

        if (parent != null) {
            val parentProject = parent.getProject(root)

            root.depend(MOD_MODULE_CONFIGURATION_NAME, module.project)
            root.depend(MODULE_DEP_CONFIGURATION_NAME, module.project)
            if (base == null) {
                parentProject.depend(
                    SHADE_CONFIGURATION_NAME,
                    parentProject.dependencies.project(mapOf("path" to project.path, "configuration" to FINAL_ARCHIVES_CONFIGURATION_NAME))
                )

                println("Found base project for $name at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                // If this is not the base, then we include it with the parent
                parentProject.depend(
                    INCLUDE_CONFIGURATION_NAME,
                    parentProject.dependencies.project(mapOf("path" to project.path, "configuration" to FINAL_ARCHIVES_CONFIGURATION_NAME))
                )

                // Make all modules that are not the base depend on it
                module.project.depend(MOD_MODULE_CONFIGURATION_NAME, base.project.moduleParent() ?: base.project)
                module.project.depend(MODULE_DEP_CONFIGURATION_NAME, base.project.moduleParent() ?: base.project)
                println("Found $name module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }
        }

        module.dependencies {
            (project.dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, this) as ProjectDependency).isTransitive = false
        }

        if (parent != null || base != null && base.project == project) {
            val infoTask = project.tasks.register(modInfo.name, modInfo.type.java) {
                it.moduleData.set(module)
                it.rootData.set(root.extensions.getByType(UnifiedBuildsExtension::class.java))
            }

            @Suppress("UnstableApiUsage")
            project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) { task ->
                val output = infoTask.flatMap(AbstractModInfoTask::destinationDirectory)

                modInfo.destination?.let {
                    task.from(output) {
                        task.into(it)
                    }
                } ?: task.from(output)

                task.dependsOn(infoTask)
            }
        }
    }

    fun Project.withProject(action: Project.() -> Unit) = getProject(project).action()
    fun getProject(project: Project) = project.childProjects[name] ?: project

    companion object {
        const val REMAP_JAR_TASK_NAME = "remapJar"
        const val FINAL_ARCHIVES_CONFIGURATION_NAME = "finalArchives"
        const val SHADE_CONFIGURATION_NAME = "shade"
        const val INCLUDE_CONFIGURATION_NAME = Constants.Configurations.INCLUDE

        private fun Project.applyModuleNaming(minecraftVersion: String, modVersion: String, platformName: String, root: Project, module: UnifiedBuildsModuleExtension) {
            fun Project.archivesName() = extensions.getByType(BasePluginExtension::class.java).archivesName

            if (root == module.project) {
                if (this != root) {
                    archivesName().set(root.archivesName())
                    afterEvaluate {
                        group = module.project.group
                    }
                }
            } else {
                if (this == module.project) {
                    afterEvaluate {
                        archivesName().set("${root.archivesName().get()}${archivesName().get()}")
                    }
                } else {
                    archivesName().set(module.project.archivesName())
                    afterEvaluate {
                        group = module.project.group
                    }
                }
            }

            project.version = "$minecraftVersion-${modVersion}$platformName"
        }
    }

    data class ModInfoData(val name: String, val type: KClass<out AbstractModInfoTask>, val destination: String? = null)
}

class ProjectPlatform(val project: Project, val platform: Platform)

package net.msrandom.unifiedbuilds.platforms

import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.platforms.Platform.Companion.applyModuleNaming
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class Platform(val name: String, val loaderVersion: String) {
    open fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
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

        if (!module.named && module.project != project) {
            module.project.applyModuleNaming(version, "", root, module)
            module.named = true
        }

        project.applyModuleNaming(version, "-$name", root, module)
    }

    protected fun addOptimizedJar(
        project: Project,
        root: Project,
        jar: TaskProvider<out Jar>,
        remapJar: TaskProvider<out Task>,
        warn: Boolean,
        remapInput: () -> Property<RegularFile>
    ) {
        val optimizeJar = project.tasks.register("optimizeJar", OptimizeJarTask::class.java) {
            it.dependsOn(jar)
            it.input.set(jar.flatMap(Jar::getArchiveFile))
            it.owningProject.set(root)

            remapInput().set(it.archiveFile)
            it.finalizedBy(remapJar)

            if (!warn) {
                it.dontwarn()
            }
        }

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            it.dependsOn(optimizeJar)
        }
    }

    fun getProject(project: Project) = project.childProjects[name] ?: project

    companion object {
        const val REMAP_JAR_NAME = "remapJar"

        fun Project.applyModuleNaming(minecraftVersion: String, platformName: String, root: Project, module: UnifiedBuildsModuleExtension) {
            fun Project.archivesName() = extensions.getByType(BasePluginExtension::class.java).archivesName

            if (root == module.project) {
                if (this != root) {
                    archivesName().set(root.archivesName())
                }
            } else {
                if (this == module.project) {
                    afterEvaluate {
                        archivesName().set("${root.archivesName().get()}${archivesName().get()}")
                    }
                } else {
                    archivesName().set(module.project.archivesName())
                }
            }

            project.version = "$minecraftVersion-${module.modVersion.get()}$platformName"
        }
    }
}

class ProjectPlatform(val project: Project, val platform: Platform)

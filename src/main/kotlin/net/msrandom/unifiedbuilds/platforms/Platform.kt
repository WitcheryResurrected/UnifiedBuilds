package net.msrandom.unifiedbuilds.platforms

import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class Platform(val name: String, val loaderVersion: String) {
    open fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        module.common?.let {
            project.extensions.getByType(SourceSetContainer::class.java).getByName(SourceSet.MAIN_SOURCE_SET_NAME) { sourceSet ->
                val common = module.project.layout.projectDirectory.dir(it)
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
    }

    protected fun addOptimizedJar(project: Project, root: Project, input: Provider<RegularFile>, remapJar: TaskProvider<out Task>, warn: Boolean, remapInput: () -> Property<RegularFile>): TaskProvider<OptimizeJarTask> {
        val optimizeJar = project.tasks.register("optimizeJar", OptimizeJarTask::class.java) {
            it.input.set(input)
            it.output.set(
                project.layout.buildDirectory.dir("minified").flatMap { dir ->
                    input.map { input -> dir.file(input.asFile.name) }
                }
            )
            it.owningProject = root

            remapInput().set(it.output)
            it.finalizedBy(remapJar)

            it.dontnote()
            if (!warn) {
                it.dontwarn()
            }
        }

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            it.dependsOn(optimizeJar)
        }

        return optimizeJar
    }

    protected fun Project.applyModuleNaming(minecraftVersion: String, platformName: String, root: Project, module: UnifiedBuildsModuleExtension) {
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

        module.project.afterEvaluate {
            project.version = "$minecraftVersion-${it.version}$platformName"
        }
    }
}

class ProjectPlatform(val project: Project, val platform: Platform)

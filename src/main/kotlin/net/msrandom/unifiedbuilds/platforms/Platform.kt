package net.msrandom.unifiedbuilds.platforms

import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

abstract class Platform(val name: String, val loaderVersion: String) {
    open fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        if (project != module.project) {
            module.project.tasks.all {
                project.tasks.findByName(it.name)?.let { task -> it.dependsOn(task) }
            }
        }

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

        project.applyModuleNaming(version, "-$name", root, module)

        if (project != module.project) {
            module.project.applyModuleNaming(version, "", root, module)
        }
    }

    private fun Project.applyModuleNaming(minecraftVersion: String, platformName: String, root: Project, module: UnifiedBuildsModuleExtension) {
        fun Project.archivesName() = extensions.getByType(BasePluginExtension::class.java).archivesName

        if (root == module.project) {
            if (this != module.project) {
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

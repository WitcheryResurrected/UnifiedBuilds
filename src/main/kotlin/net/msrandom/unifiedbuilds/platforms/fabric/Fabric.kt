package net.msrandom.unifiedbuilds.platforms.fabric

import net.fabricmc.loom.task.RemapJarTask
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.platforms.Platform
import net.msrandom.unifiedbuilds.platforms.ProjectPlatform
import net.msrandom.unifiedbuilds.tasks.fabric.FabricModJsonTask
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources

class Fabric(name: String, loaderVersion: String, private val apiVersion: String) : Platform(name, loaderVersion) {
    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        FabricPluginApplier(project)
        project.dependencies.add("minecraft", "com.mojang:minecraft:$version")
        project.dependencies.add("mappings", FabricMappingProvider.getDependency(project))
        project.dependencies.add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
        project.dependencies.add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$apiVersion")

        val defaultArchive = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java).flatMap(Jar::getArchiveFile)

        val remapJar = project.tasks.named(REMAP_JAR_NAME, RemapJarTask::class.java)

        remapJar.configure {
            it.destinationDirectory.set(project.layout.buildDirectory.dir("releases"))
        }

        project.afterEvaluate {
            project.tasks.withType(Jar::class.java).all {
                it.archiveClassifier.convention("")
                if (it.archiveClassifier.isPresent && it.archiveClassifier.get() == "dev") {
                    it.archiveClassifier.set("")
                }
            }
        }

        val optimizedJar = project.tasks.register("optimizeJar", OptimizeJarTask::class.java) {
            it.input.set(defaultArchive)
            it.output.set(
                project.layout.buildDirectory.dir("minified").flatMap { dir ->
                    it.input.map { input -> dir.file(input.asFile.name) }
                }
            )

            // Only change input for remapping if this is configured(since you could potentially want a remapped but not minified jar)
            remapJar.get().input.set(it.input)
            it.finalizedBy(remapJar)
        }

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            it.dependsOn(optimizedJar)
        }

        if (parent != null) {
            project.extensions.add("fabricEntrypoints", project.container(Array<String>::class.java))
            val parentProject = root.childProjects.getValue(parent.name)
            parentProject.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
                it.dependsOn(optimizedJar)
            }

            if (base == null) {
                // If this is the base, then we simply want the parent to depend on it, not include it
                println("Found base project for fabric at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                // If this is not the base, then we include it with the parent
                parentProject.dependencies.add("include", project)

                // Make all modules that are not the base depend on it
                project.dependencies.add("implementation", base.project)
                println("Found fabric module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }

            val createModJson = project.tasks.register("createModJson", FabricModJsonTask::class.java) {
                it.info.set(module.info)
                it.version.set(project.version.toString())
                it.license.set(root.extensions.getByType(UnifiedBuildsExtension::class.java).license)
            }

            project.tasks.withType(ProcessResources::class.java).getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
                it.dependsOn(createModJson)
                it.from(createModJson.flatMap(FabricModJsonTask::output))
            }
        } else if (base != null) {
            project.tasks.withType(Jar::class.java) { jar ->
                jar.from(base.project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
            }
        }
    }

    companion object {
        private const val REMAP_JAR_NAME = "remapJar"
    }
}

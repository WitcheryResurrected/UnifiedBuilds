package net.msrandom.unifiedbuilds.platforms.fabric

import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.Constants
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.platforms.Platform
import net.msrandom.unifiedbuilds.platforms.ProjectPlatform
import net.msrandom.unifiedbuilds.tasks.fabric.FabricModJsonTask
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources

class Fabric(name: String, loaderVersion: String, private val apiVersion: String) : Platform(name, loaderVersion) {
    class Entrypoint(val name: String, val points: Collection<String>) {
        operator fun component0() = name
        operator fun component1() = points
    }

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        if (project != module.project) {
            module.project.applyModuleNaming(version, "", root, module)
        }

        project.applyModuleNaming(version, "-$name", root, module)

        project.apply {
            it.plugin("fabric-loom")
        }

        FabricMappingProvider.disableRemaps(project)

        project.dependencies.add(Constants.Configurations.MINECRAFT, "com.mojang:minecraft:$version")
        project.dependencies.add(Constants.Configurations.MAPPINGS, FabricMappingProvider.getDependency(project))
        project.dependencies.add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
        project.dependencies.add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$apiVersion")

        if (parent != null) {
            val parentProject = root.childProjects[parent.name] ?: root
            project.extensions.add("fabricEntrypoints", project.container(Entrypoint::class.java))

            parentProject.dependencies.add("implementation", project)
            if (base == null) {
                // If this is the base, then we simply want the parent to depend on it, not include it
                println("Found base project for fabric at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                // If this is not the base, then we include it with the parent
                parentProject.dependencies.add(Constants.Configurations.INCLUDE, project)

                // Make all modules that are not the base depend on it
                project.dependencies.add("implementation", base.project)
                println("Found fabric module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }

            val createModJson = project.tasks.register("createModJson", FabricModJsonTask::class.java) {
                it.info.set(module.info)

                // This task would get created before the project finishes evaluating, so version should be the unmodified one set by the user
                it.version.set(project.version.toString())
                it.license.set(root.extensions.getByType(UnifiedBuildsExtension::class.java).license)
            }

            @Suppress("UnstableApiUsage")
            project.tasks.withType(ProcessResources::class.java).getByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
                it.dependsOn(createModJson)
                it.from(createModJson.flatMap(FabricModJsonTask::output))
            }
        } else if (base != null) {
            project.tasks.withType(Jar::class.java) { jar ->
                jar.from(base.project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
            }
        }

        val remapJar = project.tasks.named(REMAP_JAR_NAME, RemapJarTask::class.java)
        val jarTask = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
        val defaultArchive = jarTask.flatMap(Jar::getArchiveFile)

        project.tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME).dependsOn(remapJar)

        remapJar.configure {
            it.dependsOn(jarTask)
            it.destinationDirectory.set(project.layout.buildDirectory.dir("releases"))
            it.addNestedDependencies.set(true)
            it.input.set(defaultArchive)
        }

        addOptimizedJar(project, root, defaultArchive, remapJar, parent != null) { remapJar.get().input }
        // project.artifacts.add("archives", remapJar.get())

/*            project.tasks.withType(OptimizeJarTask::class.java).all {
                project.artifacts.add("archives", it.output)
            }*/
    }

    companion object {
        private const val REMAP_JAR_NAME = "remapJar"
    }
}

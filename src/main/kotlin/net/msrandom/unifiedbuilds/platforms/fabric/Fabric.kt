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
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import sun.tools.jar.resources.jar

class Fabric(name: String, loaderVersion: String, private val apiVersion: String) : Platform(name, loaderVersion) {
    class Entrypoint(val name: String, val points: Collection<String>) {
        operator fun component0() = name
        operator fun component1() = points
    }

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        project.apply {
            it.plugin("fabric-loom")
        }

        FabricMappingProvider.disableRemaps(project)

        project.dependencies.add(Constants.Configurations.MINECRAFT, "com.mojang:minecraft:$version")
        project.dependencies.add(Constants.Configurations.MAPPINGS, FabricMappingProvider.getDependency(project))
        project.dependencies.add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
        project.dependencies.add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$apiVersion")

        if (parent != null) {
            val parentProject = parent.getProject(root)
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
                if (base != null) {
                    val baseProject = root.extensions.getByType(UnifiedBuildsExtension::class.java).baseProject.get()
                    it.baseData.set(baseProject.extensions.getByType(UnifiedBuildsModuleExtension::class.java))
                }
                it.moduleData.set(module)
                it.license.set(root.extensions.getByType(UnifiedBuildsExtension::class.java).license)
            }

            @Suppress("UnstableApiUsage")
            project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) {
                it.dependsOn(createModJson)
                it.from(createModJson.flatMap(FabricModJsonTask::output))
            }
        } else if (base != null) {
            project.tasks.withType(Jar::class.java) { jar ->
                jar.from(base.project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
            }
        }

        val remapJar = project.tasks.named(REMAP_JAR_NAME, RemapJarTask::class.java)
        val jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)

        project.tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
            it.dependsOn(remapJar)
        }

        project.tasks.withType(Jar::class.java).matching { it !is RemapJarTask }.all {
            it.archiveClassifier.convention("dev")
        }

        remapJar.configure {
            it.dependsOn(jar)
            it.input.set(jar.flatMap(Jar::getArchiveFile))
            it.addNestedDependencies.set(true)
        }

        addOptimizedJar(project, root, jar, remapJar, parent != null) { remapJar.get().input }
        project.artifacts.add("archives", remapJar)
    }
}

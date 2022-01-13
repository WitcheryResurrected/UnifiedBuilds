package net.msrandom.unifiedbuilds.platforms

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.Constants
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import net.msrandom.unifiedbuilds.tasks.RemapTask
import net.msrandom.unifiedbuilds.tasks.fabric.FabricModJsonTask
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources

class Fabric(name: String, loaderVersion: String, private val apiVersion: String) : Platform(name, loaderVersion) {
    override val remapTaskType: Class<out DefaultTask>
        get() = RemapJarTask::class.java

    override val DefaultTask.remap: RemapTask
        get() {
            val remapJar = this as RemapJarTask
            return object : RemapTask {
                override fun getProject() = remapJar.project
                override fun getInput() = remapJar.input
                override val archiveBaseName = remapJar.archiveBaseName
                override val archiveClassifier = remapJar.archiveClassifier
                override val archiveVersion = remapJar.archiveVersion
                override val archiveFileName = remapJar.archiveFileName
                override val archiveFile = remapJar.archiveFile as RegularFileProperty
                override val destinationDirectory = remapJar.destinationDirectory
                override val archiveAppendix = remapJar.archiveAppendix
                override val archiveExtension = remapJar.archiveExtension
            }
        }

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        project.apply {
            it.plugin(LoomGradlePluginBootstrap::class.java)
        }

        val loom = project.extensions.getByType(LoomGradleExtensionAPI::class.java)
        loom.remapArchives.set(false)

        project.dependencies.add(Constants.Configurations.MINECRAFT, "com.mojang:minecraft:$version")
        project.dependencies.add(Constants.Configurations.MAPPINGS, loom.officialMojangMappings())
        project.dependencies.add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
        project.dependencies.add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$apiVersion")

        if (parent != null || base != null && base.project == project) {
            project.extensions.add("fabricEntrypoints", project.container(Entrypoint::class.java))
        }

        if (parent != null) {
            val parentProject = parent.getProject(root)

            project.gradle.projectsEvaluated {
                parentProject.tasks.all {
                    project.tasks.matching { task -> task.name == it.name }.all { task ->
                        it.dependsOn(task)
                    }
                }
            }

            parentProject.dependencies.add("runtimeOnly", project)
            if (base == null) {
                // If this is the base, then we simply want the parent to depend on it, not include it
                println("Found base project for fabric at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                // If this is not the base, then we include it with the parent
                parentProject.dependencies.add(Constants.Configurations.INCLUDE, project)

                // Make all modules that are not the base depend on it
                project.dependencies.add("api", base.project)
                println("Found fabric module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }
        } else if (base != null) {
            project.applyTaskFixes(base.project)
        }

        if (parent != null || base != null && base.project == project) {
            val modJson = project.tasks.register("createModJson", FabricModJsonTask::class.java) {
                val unifiedBuilds = root.extensions.getByType(UnifiedBuildsExtension::class.java)
                if (base != null) {
                    it.baseData.set(unifiedBuilds.baseProject.get().extensions.getByType(UnifiedBuildsModuleExtension::class.java))
                }
                it.moduleData.set(module)
                it.rootData.set(unifiedBuilds)
            }

            @Suppress("UnstableApiUsage")
            project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) { task ->
                task.from(modJson.flatMap(FabricModJsonTask::destinationDirectory))
                task.dependsOn(modJson)
            }
        }

        val jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
        val optimizeJar = project.tasks.register(OPTIMIZED_JAR_NAME, OptimizeJarTask::class.java) {
            it.archiveClassifier.set("dev")
            it.dependsOn(jar)
            it.input.set(jar.flatMap(Jar::getArchiveFile))
        }

        val remapJar = project.tasks.named(REMAP_JAR_NAME, RemapJarTask::class.java)
        val remapOptimizedJar = project.tasks.register(REMAP_OPTIMIZED_JAR_NAME, RemapJarTask::class.java) {
            it.dependsOn(optimizeJar)
            it.input.set(optimizeJar.flatMap(OptimizeJarTask::archiveFile))
            it.addNestedDependencies.set(true)
        }

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            it.dependsOn(remapJar)
            it.dependsOn(remapOptimizedJar)
        }

        project.tasks.withType(Jar::class.java).matching { it !is RemapJarTask }.all {
            it.applyJarDefaults(root)
        }

        remapJar.configure {
            it.archiveClassifier.set("fat")
            it.dependsOn(jar)
            it.input.set(jar.flatMap(Jar::getArchiveFile))
            it.addNestedDependencies.set(true)
        }

        project.artifacts.add("archives", remapJar)
        project.artifacts.add("archives", remapOptimizedJar)
    }

    class Entrypoint(val name: String, val points: Collection<String>) {
        operator fun component0() = name
        operator fun component1() = points
    }
}

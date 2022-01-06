package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.common.util.MojangLicenseHelper
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.UserDevPlugin
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.OptimizeJarTask
import net.msrandom.unifiedbuilds.tasks.RemapTask
import net.msrandom.unifiedbuilds.tasks.forge.MCModInfoTask
import net.msrandom.unifiedbuilds.tasks.forge.ModsTomlTask
import net.msrandom.unifiedbuilds.tasks.forge.RemapForgeArtifactTask
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import wtf.gofancy.fancygradle.FancyExtension
import wtf.gofancy.fancygradle.FancyGradle

class Forge(name: String, loaderVersion: String) : Platform(name, loaderVersion) {
    override val remapTaskType: Class<out DefaultTask>
        get() = RemapForgeArtifactTask::class.java

    override val DefaultTask.remap: RemapTask
        get() = this as RemapForgeArtifactTask

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        val versionParts = version.split('.')
        val subversion = versionParts[1].toInt()
        val legacy = subversion <= 12

        project.apply {
            it.plugin(UserDevPlugin::class.java)
        }

        val minecraft = project.extensions.getByType(UserDevExtension::class.java)

        project.dependencies.add("minecraft", "net.minecraftforge:forge:$version-$loaderVersion")

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val coreMod = if (legacy) {
            val baseProject = base?.project ?: project
            val loadingPlugins = baseProject.extensions.getByType(SourceSetContainer::class.java)
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.matching { it.include("coremod.txt") }

            loadingPlugins.takeUnless { it.isEmpty }?.singleFile?.readText()?.trim()
        } else {
            null
        }

        val accessTransformers = main.resources.matching {
            it.include("META-INF/accesstransformer.cfg")
        }

        if (!accessTransformers.isEmpty) {
            minecraft.accessTransformer(accessTransformers.singleFile)
        }

        if (parent != null) {
            val parentProject = parent.getProject(root)

            project.gradle.projectsEvaluated {
                val runTasks = minecraft.runs.mapTo(hashSetOf(), RunConfig::getTaskName)
                parentProject.tasks.matching { it.name !in runTasks }.all {
                    project.tasks.matching { task -> task.name == it.name }.all { task ->
                        it.dependsOn(task)
                    }
                }
            }

            parentProject.dependencies.add("runtimeOnly", project)
            if (!accessTransformers.isEmpty) {
                parentProject.extensions.configure(UserDevExtension::class.java) {
                    it.accessTransformer(accessTransformers.singleFile)
                }
            }
            if (base == null) {
                println("Found base project for forge at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                parentProject.dependencies.add(CONTAINED_DEP_CONFIGURATION, project)

                // Make all modules that are not the base depend on it
                project.dependencies.add("implementation", base.project)
                if (!accessTransformers.isEmpty) {
                    base.project.extensions.configure(UserDevExtension::class.java) {
                        it.accessTransformer(accessTransformers.singleFile)
                    }
                }
                println("Found forge module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }
        } else if (base != null) {
            project.configurations.create(CONTAINED_DEP_CONFIGURATION)
            project.applyTaskFixes(base.project)
        }

        project.apply {
            it.plugin(FancyGradle::class.java)
        }

        if (legacy) {
            project.extensions.getByType(FancyExtension::class.java).apply {
                patches {
                    it.resources
                    it.coremods
                    it.codeChickenLib
                    it.asm
                }
            }

            minecraft.mappings("snapshot", "20180814-1.12")

            if (parent != null) {
                val outputDir = project.buildDir.resolve("redirectedOutput")
                val copyOutputs = project.tasks.register("copyOutputs", Copy::class.java) {
                    it.destinationDir = outputDir
                    it.from(main.output)
                }

                project.gradle.projectsEvaluated {
                    main.runtimeClasspath = project.files(outputDir) + main.runtimeClasspath.filter { it !in main.output }
                }

                val mcModInfo = project.tasks.register("createMcModInfo", MCModInfoTask::class.java) {
                    val unifiedBuilds = root.extensions.getByType(UnifiedBuildsExtension::class.java)
                    if (base != null) {
                        it.baseData.set(unifiedBuilds.baseProject.get().extensions.getByType(UnifiedBuildsModuleExtension::class.java))
                    }
                    it.moduleData.set(module)
                    it.rootData.set(unifiedBuilds)
                }

                @Suppress("UnstableApiUsage")
                project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) {
                    if (base == null) {
                        it.exclude("coremod.txt")
                    }
                    it.from(mcModInfo.flatMap(MCModInfoTask::destinationDirectory))
                    it.dependsOn(mcModInfo)
                }

                project.configurations.all { configuration ->
                    configuration.resolutionStrategy {
                        it.eachDependency { dependency ->
                            if (dependency.requested.group == "net.minecraftforge" && dependency.requested.name == "mergetool") {
                                dependency.useVersion("0.2.3.3")
                            }
                        }
                    }
                }

                project.tasks.matching { it.name == "prepareRuns" }.all {
                    it.dependsOn(copyOutputs)
                }
            }
        } else {
            project.afterEvaluate {
                MojangLicenseHelper.hide(it, "official", version)
            }
            minecraft.mappings("official", version)

            if (parent != null) {
                val modsToml = project.tasks.register("createModsToml", ModsTomlTask::class.java) {
                    val unifiedBuilds = root.extensions.getByType(UnifiedBuildsExtension::class.java)
                    if (base != null) {
                        it.baseData.set(unifiedBuilds.baseProject.get().extensions.getByType(UnifiedBuildsModuleExtension::class.java))
                    }
                    it.moduleData.set(module)
                    it.rootData.set(unifiedBuilds)
                }

                @Suppress("UnstableApiUsage")
                project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) { task ->
                    task.from(modsToml.flatMap(ModsTomlTask::destinationDirectory)) {
                        it.into("META-INF")
                    }
                    task.dependsOn(modsToml)
                }
            }
        }

        val createRun = { config: RunConfig ->
            config.workingDirectory(project.file("run"))

            config.property("forge.logging.console.level", "info")
            coreMod?.let { plugin ->
                config.property("fml.coreMods.load", plugin)
            }

            if (parent != null) {
                for (run in parent.getProject(root).extensions.getByType(UserDevExtension::class.java).runs) {
                    run.child(config)
                    config.parent(run)
                }

                module.info.modId.onSet {
                    config.mods.create(it) { config ->
                        config.source(main)
                    }
                }
            }
        }

        minecraft.runs.create("client") {
            createRun(it)
            it.client(true)
        }
        minecraft.runs.create("server", createRun)

        project.extensions.getByType(object : TypeOf<NamedDomainObjectContainer<RenameJarInPlace>>() {}).whenObjectAdded { task ->
            // Replace each reobf task with one that outputs to a different directory and a proguard task.
            // (jar) libs -> (proguard) minified -> (reobf) releases
            task.enabled = false

            if (task.name == "reobfJar") {
                val jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)

                project.tasks.withType(Jar::class.java) {
                    it.applyJarDefaults(root)
                    it.manifest { manifest ->
                        if (legacy) {
                            if (!accessTransformers.isEmpty) {
                                manifest.attributes(mapOf("FMLAT" to "accesstransformer.cfg"))
                            }
                        } else {
                            module.info.modId.onSet { modId ->
                                manifest.attributes(
                                    mapOf(
                                        "Implementation-Title" to modId,
                                        "Implementation-Version" to root.extensions.getByType(UnifiedBuildsExtension::class.java).modVersion.get()
                                    )
                                )
                            }
                        }
                    }
                }

                val optimizeJar = project.tasks.register(OPTIMIZED_JAR_NAME, OptimizeJarTask::class.java) {
                    it.archiveClassifier.set("dev")
                    it.dependsOn(jar)
                    it.input.set(jar.flatMap(Jar::getArchiveFile))
                }

                val remapJar = project.tasks.register(REMAP_JAR_NAME, RemapForgeArtifactTask::class.java) {
                    it.archiveClassifier.set("fat")
                    it.setDependsOn(task.dependsOn)
                    it.input.set(task.input)
                }

                val remapOptimizedJar = project.tasks.register(REMAP_OPTIMIZED_JAR_NAME, RemapForgeArtifactTask::class.java) {
                    it.dependsOn(optimizeJar)
                    it.input.set(optimizeJar.flatMap(OptimizeJarTask::archiveFile))
                }

                project.tasks.withType(RemapForgeArtifactTask::class.java) {
                    it.nestJars.set(legacy)
                }

                project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
                    it.dependsOn(remapJar)
                    it.dependsOn(remapOptimizedJar)
                }

                project.artifacts.add("archives", remapJar.flatMap(RemapForgeArtifactTask::getOutput))
            }
        }

        coreMod?.let {
            project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java) { jar ->
                jar.manifest { manifest ->
                    manifest.attributes(
                        mapOf(
                            "FMLCorePluginContainsFMLMod" to true,
                            "FMLCorePlugin" to it
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val CONTAINED_DEP_CONFIGURATION = "containedDep"
    }
}

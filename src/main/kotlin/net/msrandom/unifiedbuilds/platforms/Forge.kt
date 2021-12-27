package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.UserDevPlugin
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.forge.MCModInfoTask
import net.msrandom.unifiedbuilds.tasks.forge.ModsTomlTask
import net.msrandom.unifiedbuilds.tasks.forge.RemapForgeArtifactTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import wtf.gofancy.fancygradle.FancyExtension
import wtf.gofancy.fancygradle.FancyGradle

class Forge(name: String, loaderVersion: String) : Platform(name, loaderVersion) {
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

            parentProject.dependencies.add("implementation", project)
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
            project.tasks.withType(Jar::class.java) { jar ->
                jar.from(base.project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
            }
        }

        if (legacy) {
            project.apply {
                it.plugin(FancyGradle::class.java)
            }

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
            }
        } else {
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

            if (!legacy) {
                project.gradle.projectsEvaluated {
                    if (module.info.modId.isPresent) {
                        config.mods.create(module.info.modId.get()) { config ->
                            config.source(main)
                        }
                    }
                }
            }
        }

        minecraft.runs.create("client", createRun)
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
                            project.gradle.projectsEvaluated {
                                if (module.info.modId.isPresent) {
                                    manifest.attributes(
                                        mapOf(
                                            "Implementation-Title" to module.info.modId.get(),
                                            "Implementation-Version" to root.extensions.getByType(UnifiedBuildsExtension::class.java).modVersion.get()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                val remapJar = project.tasks.register(REMAP_JAR_NAME, RemapForgeArtifactTask::class.java) {
                    it.setDependsOn(task.dependsOn)
                    it.nestJars.set(legacy)
                    it.input.set(task.input)
                }

                addOptimizedJar(project, root, jar, remapJar, parent != null) { remapJar.get().input }

                project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
                    it.dependsOn(remapJar)
                }

                project.artifacts.add("archives", remapJar.flatMap(RemapForgeArtifactTask::getOutput))
            }
        }

        coreMod?.let {
            project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java) { jar ->
                jar.manifest {
                    it.attributes(
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

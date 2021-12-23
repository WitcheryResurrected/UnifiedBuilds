package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.UserDevPlugin
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.forge.RemapForgeArtifactTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
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

        if (legacy) {
            if (project != module.project) {
                module.project.applyModuleNaming(version, "", root, module)
            }

            project.applyModuleNaming(version, "-$name", root, module)
        }

        project.apply {
            it.plugin(UserDevPlugin::class.java)
        }

        val minecraft = project.extensions.getByType(UserDevExtension::class.java)

        project.dependencies.add("minecraft", "net.minecraftforge:forge:$version-$loaderVersion")

        val coreMod = if (legacy) {
            val baseProject = base?.project ?: project
            val loadingPlugins = baseProject.extensions.getByType(SourceSetContainer::class.java)
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .resources.matching { it.include("coremod.txt") }

            loadingPlugins.takeUnless { it.isEmpty }?.singleFile?.readText()
        } else {
            null
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

            if (base == null) {
                // Hook into processResources to allow us to exclude the custom coremod.txt file
                @Suppress("UnstableApiUsage")
                project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java).configure {
                    it.exclude("coremod.txt")
                }
            }
        } else {
            minecraft.mappings("official", version)
        }

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val accessTransformers = main.resources.matching {
            it.include("META-INF/accesstransformer.cfg")
        }

        if (!accessTransformers.isEmpty) {
            minecraft.accessTransformer(accessTransformers.singleFile)
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
                val remapJar = project.tasks.register("remapJar", RemapForgeArtifactTask::class.java) {
                    it.setDependsOn(task.dependsOn)
                    it.destinationDir.set(project.layout.buildDirectory.dir("releases"))
                    it.input.set(task.input)
                }

                addOptimizedJar(project, root, task.input, remapJar, parent != null) { remapJar.get().input }

                project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
                    it.dependsOn(remapJar)
                }
            }
        }

        coreMod?.let {
            project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java) { jar ->
                jar.manifest {
                    it.attributes(mapOf("FMLCorePlugin" to it))
                }
            }
        }

        if (parent != null) {
            val parentProject = root.childProjects[parent.name] ?: root

            parentProject.dependencies.add("implementation", project)
            if (base == null) {
                println("Found base project for forge at ${project.path}, adding as a dependency for ${parentProject.path}")
            } else {
                // Make all modules that are not the base depend on it
                project.dependencies.add("implementation", base.project)
                println("Found forge module at ${project.path}, adding as a dependency for ${parentProject.path} and depending on ${base.project.path} as the base.")
            }
        } else if (base != null) {
            project.tasks.withType(Jar::class.java) { jar ->
                jar.from(base.project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getOutput))
            }
        }
    }
}

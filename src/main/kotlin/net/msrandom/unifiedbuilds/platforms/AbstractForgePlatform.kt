package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.UserDevPlugin
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin.Companion.MOD_MODULE_CONFIGURATION_NAME
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wtf.gofancy.fancygradle.FancyExtension
import wtf.gofancy.fancygradle.FancyGradle

abstract class AbstractForgePlatform(name: String, loaderVersion: String) : Platform(name, loaderVersion) {
    protected inline fun <reified T> Project.extension(): T = extensions.getByType(T::class.java)

    protected val SourceSet.accessTransformers: FileTree
        get() = resources.matching {
            it.include("META-INF/accesstransformer.cfg")
        }

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        project.apply {
            it.plugin(UserDevPlugin::class.java)
        }

        project.apply {
            it.plugin(FancyGradle::class.java)
        }

        val minecraft = project.extensions.getByType(UserDevExtension::class.java)
        val main = project.extension<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME)

        val moduleConfiguration = project.configurations.getByName(MOD_MODULE_CONFIGURATION_NAME)
        project.configurations.create(SHADE_CONFIGURATION_NAME) {
            it.isCanBeConsumed = false
            it.isTransitive = false
        }
        project.configurations.create(INCLUDE_CONFIGURATION_NAME) {
            it.isCanBeConsumed = false
            it.isTransitive = false
        }
        project.dependencies.add("minecraft", "net.minecraftforge:forge:$version-$loaderVersion")

        if (parent != null || base != null && base.project == project) {
            val accessTransformers = main.accessTransformers

            if (!accessTransformers.isEmpty) {
                minecraft.accessTransformer(accessTransformers.singleFile)
            }
        }

        moduleConfiguration.dependencies.all {
            if (it is ProjectDependency) {
                val moduleProject = it.dependencyProject.childProjects[name] ?: it.dependencyProject
                val projectMain = moduleProject.extension<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                val projectTransformers = projectMain.accessTransformers

                if (!projectTransformers.isEmpty) {
                    minecraft.accessTransformer(projectTransformers.singleFile)
                }
            }
        }

        project.extensions.getByType(FancyExtension::class.java).apply {
            patches {
                it.resources
            }
        }

        val createRun = { config: RunConfig ->
            config.workingDirectory(project.file("run"))

            config.property("forge.logging.console.level", "info")

            config.ideaModule = run {
                var currentProject = project
                var ideaModule = currentProject.name + ".main"
                while (currentProject.parent?.also { currentProject = it } != null) {
                    ideaModule = currentProject.name + "." + ideaModule
                }
                ideaModule
            }

            if (parent != null || base != null && base.project == project) {
                module.info.modId.onSet {
                    config.mods.create(it) { config ->
                        config.source(main)
                    }
                }
            }

            module.dependencies {
                val projectMain = extension<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME)

                extension<UnifiedBuildsModuleExtension>().apply {
                    if (platforms.isEmpty()) {
                        this@dependencies.parent?.let {
                            it.extension<UnifiedBuildsModuleExtension>().info.modId.onSet { id ->
                                config.mods.create(id) { config ->
                                    config.source(projectMain)
                                }
                            }
                        }
                    } else {
                        info.modId.onSet {
                            config.mods.create(it) { config ->
                                config.source(projectMain)
                            }
                        }
                    }
                }
            }
        }

        minecraft.runs.create("client") {
            createRun(it)
            it.client(true)
        }

        minecraft.runs.create("server") {
            createRun(it)
            it.arg("nogui")
        }

        project.extensions.getByType(object : TypeOf<NamedDomainObjectContainer<RenameJarInPlace>>() {}).whenObjectAdded {
            it.enabled = false
        }

        val jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)

        val remapJar = project.tasks.register(REMAP_JAR_TASK_NAME, remapTaskType) {
            it.dependsOn(jar)
            it.input.set(jar.flatMap(Jar::getArchiveFile))
            it.shade.from(it.project.configurations.getByName(SHADE_CONFIGURATION_NAME))
        }

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { it.dependsOn(remapJar) }

        project.configurations.create(FINAL_ARCHIVES_CONFIGURATION_NAME) { it.isCanBeResolved = false }
        project.artifacts.add(FINAL_ARCHIVES_CONFIGURATION_NAME, remapJar)
        project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, remapJar)

        super.handle(version, project, root, module, base, parent)
    }
}

package net.msrandom.unifiedbuilds.platforms

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap
import net.fabricmc.loom.util.Constants
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.fabric.FabricModJsonTask
import net.msrandom.unifiedbuilds.tasks.fabric.RemapFabricArtifactTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin

class Fabric(name: String, loaderVersion: String, private val apiVersion: String) : Platform(name, loaderVersion) {
    override val remapTaskType
        get() = RemapFabricArtifactTask::class.java

    override val Jar.input: RegularFileProperty
        get() = (this as RemapFabricArtifactTask).inputFile

    override val Jar.shade: ConfigurableFileCollection
        get() = (this as RemapFabricArtifactTask).shade

    override val modInfo = ModInfoData("createModJson", FabricModJsonTask::class)

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        project.configurations.create(SHADE_CONFIGURATION_NAME) { it.isCanBeConsumed = false }

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

        loom.runConfigs.matching { it.name == "client" || it.name == "server" }.all {
            it.isIdeConfigGenerated = true
        }

        val jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
        val remapJar = project.tasks.replace(REMAP_JAR_TASK_NAME, RemapFabricArtifactTask::class.java)

        remapJar.dependsOn(jar)
        remapJar.inputFile.set(jar.flatMap(Jar::getArchiveFile))
        remapJar.shade.from(project.configurations.getByName(SHADE_CONFIGURATION_NAME))

        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { it.dependsOn(remapJar) }

        project.configurations.create(FINAL_ARCHIVES_CONFIGURATION_NAME) { it.isCanBeResolved = false }
        project.artifacts.add(FINAL_ARCHIVES_CONFIGURATION_NAME, remapJar)
        project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, remapJar)
    }

    data class Entrypoint(val name: String, val points: Collection<String>)
}

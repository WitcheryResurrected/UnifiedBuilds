package net.msrandom.unifiedbuilds.tasks.forge

import com.moandjiezana.toml.TomlWriter
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
abstract class ModsTomlTask : DefaultTask() {
    abstract val baseData: Property<UnifiedBuildsModuleExtension>
        @Internal get

    abstract val moduleData: Property<UnifiedBuildsModuleExtension>
        @Internal get

    abstract val rootData: Property<UnifiedBuildsExtension>
        @Internal get

    abstract val destinationDirectory: DirectoryProperty
        @Optional
        @OutputDirectory
        get

    init {
        apply {
            destinationDirectory.convention(project.layout.buildDirectory.dir("generatedModInfo"))
        }
    }

    @TaskAction
    fun makeToml() {
        val output = destinationDirectory.file("mods.toml").get().asFile
        val platform = moduleData.get().platforms.firstOrNull { it.name == project.name } ?: moduleData.get().platforms.first()
        val loaderVersion = platform.loaderVersion.split('.')[0]
        val modInfo = moduleData.get().info

        val dependencies = mutableListOf(
            mapOf(
                "modId" to "forge",
                "mandatory" to true,
                "versionRange" to "[$loaderVersion,)"
            )
        )

        if (baseData.isPresent) {
            dependencies.add(
                mapOf(
                    "modId" to baseData.get().info.modId.get(),
                    "mandatory" to true,
                    "ordering" to "AFTER"
                )
            )
        }

        dependencies.addAll(
            modInfo.dependencies.get().map { dependency ->
                val map = mutableMapOf(
                    "modId" to dependency.modId,
                    "mandatory" to dependency.required
                )
                dependency.version?.let {
                    map["versionRange"] = it
                }
                map
            }
        )

        val toml = mutableMapOf(
            "modLoader" to "javafml",
            "loaderVersion" to "[$loaderVersion,)",
            "license" to rootData.get().license.get(),
            "dependencies" to mapOf(
                modInfo.modId.get() to dependencies
            )
        )

        val mod = mutableMapOf("modId" to modInfo.modId.get(), "version" to "\${file.jarVersion}")
        if (modInfo.name.isPresent) mod["displayName"] = modInfo.name.get()
        if (modInfo.description.isPresent) mod["description"] = modInfo.description.get()

        if (modInfo.icon.isPresent) {
            mod["logoFile"] = modInfo.icon.get()
            toml["logoBlur"] = false
        }

        if (modInfo.url.isPresent) mod["displayURL"] = modInfo.url.get()
        if (modInfo.contributors.get().isNotEmpty()) mod["credits"] = "Contributors: ${modInfo.contributors.get().joinToString()}"
        if (modInfo.authors.get().isNotEmpty()) mod["authors"] = modInfo.authors.get().joinToString()

        toml["mods"] = arrayOf(mod)

        TomlWriter().write(toml, output.outputStream())
    }
}

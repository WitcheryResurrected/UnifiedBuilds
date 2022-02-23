package net.msrandom.unifiedbuilds.tasks.forge

import com.moandjiezana.toml.TomlWriter
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.tasks.AbstractModInfoTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction

abstract class ModsTomlTask : AbstractModInfoTask() {
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

        moduleData.get().project.configurations.getByName(UnifiedBuildsPlugin.MODULE_DEP_CONFIGURATION_NAME).dependencies.all {
            if (it is ProjectDependency) {
                val id = it.dependencyProject.extensions.getByType(UnifiedBuildsModuleExtension::class.java).info.modId.get()
                dependencies.add(
                    mapOf(
                        "modId" to id,
                        "mandatory" to true,
                        "ordering" to "AFTER"
                    )
                )
            }
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

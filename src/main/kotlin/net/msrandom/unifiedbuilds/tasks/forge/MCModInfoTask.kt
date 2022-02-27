package net.msrandom.unifiedbuilds.tasks.forge

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.msrandom.unifiedbuilds.ModInformation
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsPlugin
import net.msrandom.unifiedbuilds.tasks.AbstractModInfoTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction
import java.util.*

abstract class MCModInfoTask : AbstractModInfoTask() {
    @TaskAction
    fun makeJson() {
        val modInfoFile = destinationDirectory.file("mcmod.info").get().asFile
        val versionPropertiesFile = destinationDirectory.file("version.properties").get().asFile
        val modInfo = moduleData.get().info
        destinationDirectory.get().asFile.mkdirs()
        versionPropertiesFile.outputStream().use {
            Properties().apply { setProperty("${modInfo.modId.get()}.version", project.version.toString()) }.store(it, null)
        }

        val json = JsonArray()
        json.add(
            JsonObject().apply {
                addProperty("modid", modInfo.modId.get())
                addProperty("version", rootData.get().modVersion.get())
                addProperty("mcversion", rootData.get().minecraftVersion.get())
                if (modInfo.name.isPresent) addProperty("name", modInfo.name.get())
                if (modInfo.description.isPresent) addProperty("description", modInfo.description.get())
                if (modInfo.url.isPresent) addProperty("url", modInfo.url.get())
                if (modInfo.icon.isPresent) addProperty("logoFile", modInfo.icon.get())

                if (modInfo.authors.get().isNotEmpty()) {
                    add("authorList", JsonArray().apply { modInfo.authors.get().forEach(::add) })
                }

                if (modInfo.contributors.get().isNotEmpty()) {
                    addProperty("credits", "Contributors: ${modInfo.contributors.get().joinToString()}")
                }

                fun ModInformation.Dependency.toDependencyNotation() = buildString {
                    append(modId)
                    version?.let {
                        append('@')
                        append(it)
                    }
                }

                val requiredMods = JsonArray()
                val dependencies = JsonArray()

                val baseData = rootData.get().baseProject.get().extensions.getByType(UnifiedBuildsModuleExtension::class.java)
                val basePlatform = baseData.platforms.firstOrNull { it.name == project.name } ?: baseData.platforms.first()
                if (basePlatform.getProject(baseData.project) != project) {
                    addProperty("parent", baseData.info.modId.get())
                }

                moduleData.get().project.configurations.getByName(UnifiedBuildsPlugin.MODULE_DEP_CONFIGURATION_NAME).dependencies.all {
                    if (it is ProjectDependency) {
                        val id = it.dependencyProject.extensions.getByType(UnifiedBuildsModuleExtension::class.java).info.modId.get()
                        requiredMods.add(id)
                        dependencies.add(id)
                    }
                }

                if (project != moduleData.get().project) {
                    modInfo.dependencies.addAll(project.extensions.getByType(UnifiedBuildsModuleExtension::class.java).info.dependencies.get())
                }

                if (modInfo.dependencies.get().isNotEmpty()) {
                    for (dependency in modInfo.dependencies.get()) {
                        val notation = dependency.toDependencyNotation()
                        if (dependency.required) {
                            requiredMods.add(notation)
                        }
                        dependencies.add(notation)
                    }
                }

                addProperty("useDependencyInformation", true)
                if (!requiredMods.isEmpty) add("requiredMods", requiredMods)
                if (!dependencies.isEmpty) add("dependencies", dependencies)
            }
        )

        modInfoFile.writeText(Gson().toJson(json))
    }
}

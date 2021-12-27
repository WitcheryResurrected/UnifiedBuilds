package net.msrandom.unifiedbuilds.tasks.forge

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.msrandom.unifiedbuilds.ModInformation
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import java.util.*

@CacheableTask
abstract class MCModInfoTask : DefaultTask() {
    abstract val baseData: Property<UnifiedBuildsModuleExtension>
        @Internal get

    abstract val moduleData: Property<UnifiedBuildsModuleExtension>
        @Internal get

    abstract val version: Property<String>
        @Input get

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
                addProperty("version", version.get())
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
                    if (version != null) {
                        append('@')
                        append(version)
                    }
                }

                val requiredMods = JsonArray()
                val dependants = JsonArray()
                val dependencies = JsonArray()

                if (baseData.isPresent) {
                    val baseId = baseData.get().info.modId.get()
                    addProperty("parent", baseId)
                    requiredMods.add(baseId)
                    dependants.add(baseId)
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
                if (!dependants.isEmpty) add("dependants", dependants)
                if (!dependencies.isEmpty) add("dependencies", dependencies)
            }
        )

        modInfoFile.writeText(Gson().toJson(json))
    }
}

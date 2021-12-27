package net.msrandom.unifiedbuilds.tasks.fabric

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.platforms.fabric.Fabric
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.*

@CacheableTask
abstract class FabricModJsonTask : DefaultTask() {
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
            destinationDirectory.convention(project.layout.buildDirectory.dir("generated-fabric-json"))
        }
    }

    @TaskAction
    fun makeJson() {
        val file = destinationDirectory.file("fabric.mod.json").get().asFile
        file.parentFile.mkdirs()
        val json = JsonObject().apply {
            val modInfo = moduleData.get().info
            addProperty("schemaVersion", 1)
            addProperty("id", modInfo.modId.get())
            addProperty("version", rootData.get().modVersion.get())
            if (modInfo.name.isPresent) addProperty("name", modInfo.name.get())
            if (modInfo.description.isPresent) addProperty("description", modInfo.description.get())

            if (modInfo.authors.get().isNotEmpty())
                add("authors", JsonArray().apply { modInfo.authors.get().forEach(::add) })

            if (modInfo.contributors.get().isNotEmpty())
                add("contributors", JsonArray().apply { modInfo.contributors.get().forEach(::add) })

            if (modInfo.url.isPresent) {
                add(
                    "contact",
                    JsonObject().apply {
                        addProperty("homepage", modInfo.url.get())
                    }
                )
            }

            addProperty("license", rootData.get().license.get())
            if (modInfo.icon.isPresent) addProperty("icon", modInfo.icon.get())

            val resources = project.extensions.getByType(SourceSetContainer::class.java).getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources
            val accessWideners = resources.matching { it.include("${modInfo.modId}.accessWideners") }
            if (!accessWideners.isEmpty) {
                val accessWidener = accessWideners.singleFile
                for (srcDir in resources.srcDirs) {
                    if (accessWidener.absolutePath.startsWith(srcDir.absolutePath)) {
                        // Found the source directory that contained the file
                        addProperty("accessWidener", accessWidener.toRelativeString(srcDir))
                        break
                    }
                }
            }

            if (project != moduleData.get().project) {
                val currentModuleInfo = project.extensions.getByType(UnifiedBuildsModuleExtension::class.java).info
                modInfo.mixins.addAll(currentModuleInfo.mixins.get())
                modInfo.dependencies.addAll(currentModuleInfo.dependencies.get())
            }

            if (modInfo.mixins.get().isNotEmpty())
                add("mixins", JsonArray().apply { modInfo.mixins.get().forEach(::add) })

            val depends = JsonObject()

            // If there is no platform with the project name, that means there's only one platform,
            // since multiplatform projects are required to include child projects with matching names
            val platform = moduleData.get().platforms.firstOrNull { it.name == project.name }
                ?: moduleData.get().platforms.first()

            depends.addProperty("fabricloader", ">=${platform.loaderVersion}")
            depends.addProperty("fabric", "*")

            if (modInfo.dependencies.get().isNotEmpty()) {
                val suggests = JsonObject()
                for (dependency in modInfo.dependencies.get()) {
                    val key = dependency.modId
                    val version = dependency.version ?: "*"
                    if (dependency.required) {
                        depends.addProperty(key, version)
                    } else {
                        suggests.addProperty(key, version)
                    }
                }
                if (suggests.size() != 0) add("suggests", suggests)
            }
            if (baseData.isPresent) {
                depends.addProperty(baseData.get().info.modId.get(), "*")
            }
            add("depends", depends)

            val entrypointsExtesion = project.extensions.getByType(object : TypeOf<NamedDomainObjectContainer<Fabric.Entrypoint>>() {})
            if (entrypointsExtesion.isNotEmpty() && entrypointsExtesion.any { it.points.isNotEmpty() }) {
                val entrypointsObject = JsonObject()
                for (entrypoints in entrypointsExtesion) {
                    if (entrypoints.points.isNotEmpty()) {
                        entrypointsObject.add(entrypoints.name, JsonArray().apply { entrypoints.points.forEach(::add) })
                    }
                }
            }
        }
        file.writeText(Gson().toJson(json))
    }
}

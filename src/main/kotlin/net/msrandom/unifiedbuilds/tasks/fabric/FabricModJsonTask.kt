package net.msrandom.unifiedbuilds.tasks.fabric

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.msrandom.unifiedbuilds.ModInformation
import net.msrandom.unifiedbuilds.platforms.fabric.Fabric
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.*

@CacheableTask
abstract class FabricModJsonTask : DefaultTask() {
    abstract val info: Property<ModInformation>
        @Input get

    abstract val version: Property<String>
        @Input get

    abstract val license: Property<String>
        @Input get

    val output: Provider<RegularFile> = project.layout.buildDirectory.dir("generated_fabric_jsons").map { it.file("fabric.mod.json") }
        @Internal get

    @TaskAction
    fun makeJson() {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        val json = JsonObject().apply {
            val modInfo = info.get()
            addProperty("schemaVersion", 1)
            addProperty("id", modInfo.modId.get())
            addProperty("version", version.get())
            if (modInfo.name.isPresent) addProperty("name", modInfo.name.get())
            if (modInfo.description.isPresent) addProperty("description", modInfo.description.get())

            if (modInfo.authors.isPresent && modInfo.authors.get().isNotEmpty())
                add("authors", JsonArray().apply { modInfo.authors.get().forEach(::add) })

            if (modInfo.contributors.isPresent && modInfo.contributors.get().isNotEmpty())
                add("contributors", JsonArray().apply { modInfo.contributors.get().forEach(::add) })

            if (modInfo.url.isPresent) {
                add(
                    "contact",
                    JsonObject().apply {
                        addProperty("homepage", modInfo.url.get())
                    }
                )
            }

            addProperty("license", license.get())
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

            if (modInfo.mixins.isPresent && modInfo.mixins.get().isNotEmpty())
                add("mixins", JsonArray().apply { modInfo.mixins.get().forEach(::add) })

            if (modInfo.dependencies.isPresent && modInfo.dependencies.get().isNotEmpty()) {
                val depends = JsonObject()
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
                if (depends.size() != 0) add("depends", depends)
                if (suggests.size() != 0) add("suggests", suggests)
            }

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

package net.msrandom.unifiedbuilds.tasks.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.Pair
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.ZipUtils.UnsafeUnaryOperator
import net.msrandom.unifiedbuilds.platforms.Platform
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.util.stream.Stream

abstract class RemapFabricArtifactTask : RemapJarTask() {
    abstract val shade: ConfigurableFileCollection
        @InputFiles get

    init {
        apply {
            from(project.configurations.getByName(Platform.INCLUDE_CONFIGURATION_NAME)) {
                it.into("META-INF/jars")
            }
        }
    }

    @TaskAction
    fun makeJar() {
        if (!shade.isEmpty) {
            from(shade.map(project::zipTree))
            manifest.from(
                shade.map { shaded ->
                    project.zipTree(shaded).matching { it.include("META-INF/MANIFEST.MF") }.singleFile
                }
            )
        }

        super.copy()
    }

    @TaskAction
    fun nestJars() {
        ZipUtils.transformJson(
            JsonObject::class.java,
            archiveFile.get().asFile.toPath(),
            Stream.of(
                Pair(
                    "fabric.mod.json",
                    UnsafeUnaryOperator { json ->
                        val nestedJars = json.getAsJsonArray("jars") ?: JsonArray().also { json.add("jars", it) }
                        for (file in project.configurations.getByName(Platform.INCLUDE_CONFIGURATION_NAME)) {
                            nestedJars.add(JsonObject().apply { addProperty("file", "META-INF/jars/${file.name}") })
                        }
                        json.also { it.add("jars", nestedJars) }
                    }
                )
            )
        )
    }
}

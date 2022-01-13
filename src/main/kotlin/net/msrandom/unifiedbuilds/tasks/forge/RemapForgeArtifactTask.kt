package net.msrandom.unifiedbuilds.tasks.forge

import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar
import net.msrandom.unifiedbuilds.platforms.Forge
import net.msrandom.unifiedbuilds.platforms.Platform
import net.msrandom.unifiedbuilds.tasks.ProjectJarArchive.Companion.setConventions
import net.msrandom.unifiedbuilds.tasks.RemapTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.zeroturnaround.zip.FileSource
import org.zeroturnaround.zip.ZipUtil
import org.zeroturnaround.zip.commons.FileUtils
import org.zeroturnaround.zip.transform.StreamZipEntryTransformer
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry
import java.io.*
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private typealias MergeAction = (existing: InputStream, overriding: InputStream) -> ByteArray

// Remap and include contained deps, similarly to the fabric RemapJarTask
@Suppress("UNCHECKED_CAST")
abstract class RemapForgeArtifactTask : ReobfuscateJar(), RemapTask {
    private val mergeActions = hashMapOf<String, MergeAction>()

    val duplicatesStrategy = DuplicatesStrategy.FAIL
        @Optional
        @Input
        get

    abstract val nestJars: Property<Boolean>
        @Optional
        @Input
        get

    init {
        classpath.from(project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath))
        srg.set(project.tasks.named("createMcpToSrg", GenerateSRG::class.java).flatMap(GenerateSRG::getOutput))
        apply {
            setConventions()
            keepData()
            keepPackages()
        }

        handleMerge("META-INF/MANIFEST.MF") { existing, overriding ->
            val manifest = Manifest(existing)
            val newManifest = Manifest(overriding)
            fun mergeManifests(attributes: Attributes, existing: Attributes) {
                for ((attributeKey, value) in attributes) {
                    if (attributeKey !in existing) existing[attributeKey] = value
                }
            }

            for ((key, attributes) in newManifest.entries) {
                manifest.entries[key]?.let {
                    mergeManifests(attributes, it)
                } ?: run {
                    manifest.entries[key] = attributes
                }
            }
            mergeManifests(newManifest.mainAttributes, manifest.mainAttributes)
            ByteArrayOutputStream().use {
                manifest.write(it)
                it.toByteArray()
            }
        }

        handleMerge("META-INF/mods.toml") { existing, overriding ->
            fun addToml(original: Toml, toAdd: Map<String, Any>): Map<String, Any> {
                val newMap = original.toMap()
                for ((key, value) in toAdd) {
                    if (value is Map<*, *>) {
                        newMap[key] = addToml(original.getTable(key) ?: Toml(), value as Map<String, Any>)
                    } else if (value is Collection<*>) {
                        val old = newMap[key] as? Collection<Any>
                        if (old != null) {
                            (value as MutableCollection<Any>).addAll(old)
                        }
                        newMap[key] = value
                    } else if (key !in newMap) {
                        newMap[key] = value
                    }
                }
                return newMap
            }

            ByteArrayOutputStream().use {
                TomlWriter().write(addToml(Toml().read(existing), Toml().read(overriding).toMap()), it)
                it.toByteArray()
            }
        }

        handleMerge("pack.mcmeta") { existing, _ -> existing.readAllBytes() }
    }

    fun handleMerge(name: String, action: MergeAction) {
        mergeActions[name] = action
    }

    @TaskAction
    fun nestJars() {
        project.configurations.findByName(Forge.CONTAINED_DEP_CONFIGURATION)?.let { containedDeps ->
            val modJar = archiveFile.asFile.get()
            val files = containedDeps.dependencies.asSequence()
                .filterIsInstance<ProjectDependency>()
                .mapNotNull {
                    val remapJar = it.dependencyProject.tasks.withType(RemapForgeArtifactTask::class.java).findByName(Platform.REMAP_JAR_NAME)
                    remapJar?.let { task -> it to task }
                }

            if (nestJars.getOrElse(false)) {
                val metaFilesDir = project.buildDir.resolve("metaFiles")
                val metaFiles = mutableListOf<File>()
                metaFilesDir.mkdirs()

                val fileSources = files.onEach { (dependency, remapJar) ->
                    val manifestFile = dependency.dependencyProject.zipTree(remapJar.archiveFile).matching { it.include("META-INF/MANIFEST.MF") }.singleFile
                    val manifest = Manifest(manifestFile.inputStream())
                    val metaFile = metaFilesDir.resolve("${remapJar.archiveFileName.get()}.meta")
                    val notation = "${dependency.group}:${dependency.name}:${dependency.version}"
                    manifest.mainAttributes.putValue("Maven-Artifact", notation)
                    manifest.write(metaFile.outputStream())
                    metaFiles.add(metaFile)
                }.toList()

                ZipUtil.transformEntries(
                    modJar,
                    arrayOf(
                        ZipEntryTransformerEntry(
                            "META-INF/MANIFEST.MF",
                            object : StreamZipEntryTransformer() {
                                override fun transform(zipEntry: ZipEntry, input: InputStream, output: OutputStream) {
                                    Manifest(input).apply {
                                        mainAttributes.putValue("ContainedDeps", fileSources.joinToString(" ") { it.second.archiveFileName.get() })
                                    }.write(output)
                                }
                            }
                        )
                    )
                )

                val sources = fileSources.mapTo(mutableListOf()) { (_, remapJar) ->
                    FileSource("META-INF/libraries/${remapJar.archiveFileName.get()}", remapJar.archiveFile.asFile.get())
                }

                for (file in metaFiles) {
                    sources.add(FileSource("META-INF/libraries/${file.name}", file))
                }

                ZipUtil.addEntries(modJar, sources.toTypedArray())
            } else {
                var temporary: File? = null
                try {
                    temporary = File.createTempFile("tmp-merge-jar", ".jar")
                    val toMerge = sequenceOf(JarFile(modJar)) + files.map { JarFile(it.second.archiveFile.asFile.get()) }
                    val mapped = toMerge.flatMap { jar ->
                        jar.entries().asSequence().map {
                            OwningEntry(it.name, jar.getInputStream(it).use(InputStream::readBytes))
                        }
                    }

                    val map = hashMapOf<String, ByteArray>()
                    for (entry in mapped) {
                        map[entry.name]?.let {
                            val mergeAction = mergeActions[entry.name]
                            if (mergeAction == null) {
                                when (duplicatesStrategy) {
                                    DuplicatesStrategy.EXCLUDE -> map.remove(entry.name)
                                    DuplicatesStrategy.WARN -> logger.warn("Duplicate entry ${entry.name}")
                                    DuplicatesStrategy.FAIL -> throw IllegalArgumentException("Duplicate entry ${entry.name}")
                                    else -> {}
                                }
                            } else {
                                ByteArrayInputStream(it).use { existingStream ->
                                    ByteArrayInputStream(entry.data).use { overridingStream ->
                                        map[entry.name] = mergeAction(existingStream, overridingStream)
                                    }
                                }
                            }
                        } ?: run {
                            map[entry.name] = entry.data
                        }
                    }

                    ZipOutputStream(temporary.outputStream()).use { output ->
                        for ((name, data) in map) {
                            output.putNextEntry(ZipEntry(name))
                            output.write(data)
                            output.closeEntry()
                        }
                    }

                    FileUtils.forceDelete(modJar)
                    FileUtils.moveFile(temporary, modJar)
                } finally {
                    FileUtils.deleteQuietly(temporary)
                }
            }
        }
    }

    @InputFile
    abstract override fun getInput(): RegularFileProperty

    override fun getOutput() = archiveFile

    private class OwningEntry(val name: String, val data: ByteArray)
}

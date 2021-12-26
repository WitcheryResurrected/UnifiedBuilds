package net.msrandom.unifiedbuilds.tasks.forge

import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar
import net.msrandom.unifiedbuilds.platforms.Forge
import net.msrandom.unifiedbuilds.platforms.Platform
import net.msrandom.unifiedbuilds.tasks.ProjectJarArchive
import net.msrandom.unifiedbuilds.tasks.ProjectJarArchive.Companion.setConventions
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.zeroturnaround.zip.FileSource
import org.zeroturnaround.zip.ZipUtil
import org.zeroturnaround.zip.transform.StreamZipEntryTransformer
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

// Remap and include contained deps, similarly to the fabric RemapJarTask
abstract class RemapForgeArtifactTask : ReobfuscateJar(), ProjectJarArchive {
    init {
        classpath.from(project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath))
        srg.set(project.tasks.named("createMcpToSrg", GenerateSRG::class.java).flatMap(GenerateSRG::getOutput))
        apply {
            setConventions()
            keepData()
            keepPackages()
        }
    }

    @TaskAction
    fun nestJars() {
        project.configurations.findByName(Forge.CONTAINED_DEP_CONFIGURATION)?.let { containedDeps ->
            val metaFilesDir = project.buildDir.resolve("metaFiles")
            val metaFiles = mutableListOf<File>()
            metaFilesDir.mkdirs()

            val modJar = archiveFile.asFile.get()
            val files = containedDeps.dependencies.asSequence()
                .filterIsInstance<ProjectDependency>()
                .mapNotNull {
                    val remapJar = it.dependencyProject.tasks.withType(RemapForgeArtifactTask::class.java).findByName(Platform.REMAP_JAR_NAME)
                    remapJar?.let { task -> it to task }
                }
                .onEach { (dependency, remapJar) ->
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
                                    mainAttributes.putValue("ContainedDeps", files.joinToString(" ") { it.second.archiveFileName.get() })
                                }.write(output)
                            }
                        }
                    )
                )
            )

            val sources = files.mapTo(mutableListOf()) { (_, remapJar) ->
                FileSource("META-INF/libraries/${remapJar.archiveFileName.get()}", remapJar.archiveFile.asFile.get())
            }

            for (file in metaFiles) {
                sources.add(FileSource("META-INF/libraries/${file.name}", file))
            }

            ZipUtil.addEntries(modJar, sources.toTypedArray())
        }
    }

    @InputFile
    abstract override fun getInput(): RegularFileProperty

    override fun getOutput() = archiveFile
}

package net.msrandom.unifiedbuilds.tasks.forge

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar
import net.msrandom.unifiedbuilds.platforms.Platform
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import shadow.org.apache.tools.zip.ZipEntry
import shadow.org.apache.tools.zip.ZipOutputStream
import javax.inject.Inject

private val FileTree.manifest
    get() = matching { it.include("META-INF/MANIFEST.MF") }.singleFile

private fun Task.getRemapper(input: RegularFileProperty, javaToolchainService: JavaToolchainService): ReobfuscateJar {
    val createMcpToSrg = project.tasks.named("createMcpToSrg", GenerateSRG::class.java)
    val tool = project.objects.property(String::class.java)
    val args = project.objects.listProperty(String::class.java)
    val classpath = project.objects.fileCollection()
    val javaLauncher = project.objects.property(JavaLauncher::class.java)
    val srg = project.objects.fileProperty()
    val output = project.objects.fileProperty()

    val reobfuscateJar = object : ReobfuscateJar() {
        override fun getTool() = tool
        override fun getArgs() = args
        override fun getClasspath() = classpath
        override fun getJavaLauncher() = javaLauncher
        override fun getInput() = input
        override fun getSrg() = srg
        override fun getOutput() = output
        override fun getJavaToolchainService() = javaToolchainService
    }

    srg.set(createMcpToSrg.flatMap(GenerateSRG::getOutput))
    classpath.from(project.extensions.getByType(SourceSetContainer::class.java).getByName(SourceSet.MAIN_SOURCE_SET_NAME).compileClasspath)
    reobfuscateJar.keepData()
    reobfuscateJar.keepPackages()

    dependsOn(createMcpToSrg)
    return reobfuscateJar
}

abstract class RemapLegacyArtifactTask : Jar() {
    abstract val input: RegularFileProperty
        @InputFile get

    abstract val shade: ConfigurableFileCollection
        @InputFiles get

    protected abstract val javaToolchainService: JavaToolchainService
        @Inject get

    private val remapper = run { getRemapper(input, javaToolchainService) }

    init {
        apply {
            from(project.configurations.getByName(Platform.INCLUDE_CONFIGURATION_NAME)) {
                it.into("META-INF/libraries")
            }
        }
    }

    @TaskAction
    fun remap() {
        remapper.apply()
        val zipTree = remapper.output.map(project::zipTree).get()
        manifest.from(zipTree.manifest)

        if (!shade.isEmpty) {
            from(shade.map(project::zipTree))
            manifest.from(
                shade.map { shaded ->
                    project.zipTree(shaded).matching { it.include("META-INF/MANIFEST.MF") }.singleFile
                }
            )
        }

        val containedManifest = StringBuilder()
        for (file in project.configurations.getByName(Platform.INCLUDE_CONFIGURATION_NAME)) {
            if (containedManifest.isNotEmpty()) {
                containedManifest.append(", ")
            }
            containedManifest.append(file.name)
        }

        if (containedManifest.isNotEmpty()) {
            manifest.attributes(mapOf("ContainedDeps" to containedManifest.toString()))
        }

        from(zipTree)
        super.copy()
    }
}

abstract class RemapForgeArtifactTask : ShadowJar() {
    abstract val input: RegularFileProperty
        @InputFile get

    abstract val shade: ConfigurableFileCollection
        @InputFiles get

    protected abstract val javaToolchainService: JavaToolchainService
        @Inject get

    private val remapper = run { getRemapper(input, javaToolchainService) }

    init {
        apply {
            configurations = listOf(project.configurations.getByName(Platform.INCLUDE_CONFIGURATION_NAME))

            mergeServiceFiles().mergeGroovyExtensionModules()
            transform(ModsTomlTransformer())
        }
    }

    @TaskAction
    fun remap() {
        remapper.apply()
        val zipTree = remapper.output.map(project::zipTree).get()
        manifest.from(zipTree.manifest)

        if (!shade.isEmpty) {
            from(shade.map(project::zipTree))
            manifest.from(
                shade.map { shaded ->
                    project.zipTree(shaded).matching { it.include("META-INF/MANIFEST.MF") }.singleFile
                }
            )
        }

        from(zipTree)
        super.copy()
    }

    class ModsTomlTransformer : Transformer {
        private var transformedResources = false
        private val toml = mutableMapOf<String, Any>()

        override fun getName() = "Mod Info Merger"
        override fun canTransformResource(element: FileTreeElement) = "META-INF/mods.toml" in element.name
        override fun hasTransformedResource() = transformedResources

        override fun transform(context: TransformerContext) {
            transformedResources = true
            addToml(toml, Toml().read(context.`is`).toMap())
        }

        override fun modifyOutputStream(output: ZipOutputStream, preserveFileTimestamps: Boolean) {
            val entry = ZipEntry("META-INF/mods.toml")
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            output.putNextEntry(entry)
            TomlWriter().write(toml, output)
            output.closeEntry()
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            @JvmStatic
            private fun addToml(original: MutableMap<String, Any>?, toAdd: Map<String, Any>): Map<String, Any> {
                val newMap = original ?: mutableMapOf()
                for ((key, value) in toAdd) {
                    if (value is Map<*, *>) {
                        newMap[key] = addToml(newMap[key] as? MutableMap<String, Any>, value as Map<String, Any>)
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
        }
    }
}

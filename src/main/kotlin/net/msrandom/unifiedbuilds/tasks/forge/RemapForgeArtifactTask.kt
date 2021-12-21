package net.msrandom.unifiedbuilds.tasks.forge

import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

abstract class RemapForgeArtifactTask : ReobfuscateJar() {
    abstract val destinationDir: DirectoryProperty
        @OutputDirectory get

    init {
        classpath.from(project.extensions.getByType(SourceSetContainer::class.java).named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath))
        srg.set(project.tasks.named("createMcpToSrg", GenerateSRG::class.java).flatMap(GenerateSRG::getOutput))
        apply {
            output.set(destinationDir.flatMap { dir -> input.map { dir.file(it.asFile.name) } })
        }
    }

    @InputFile
    abstract override fun getInput(): RegularFileProperty
}

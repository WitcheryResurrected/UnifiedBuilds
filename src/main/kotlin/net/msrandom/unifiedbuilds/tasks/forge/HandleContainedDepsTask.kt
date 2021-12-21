package net.msrandom.unifiedbuilds.tasks.forge

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input

// TODO maybe make this cachable?
abstract class HandleContainedDepsTask : Delete() {
    abstract val modules: Property<Collection<Project>>
        @Input get

    abstract val containerProject: Property<Project>
        @Input get

    init {
        apply {
            delete(containerProject.flatMap { it.layout.buildDirectory.dir("meta_files") })

            doLast {
                val metaFiles = containerProject.get().buildDir.resolve("meta_files")
                metaFiles.mkdirs()
                modules.get().forEach { project ->
                    project.run {
                        val artifact = artifacts.add("archives", tasks.named(JavaPlugin.JAR_TASK_NAME)).file
                        val manifest = zipTree(artifact).matching { it.include("META-INF/MANIFEST.MF") }.singleFile
                        val metaFile = metaFiles.resolve("${artifact.name}.meta")
                        metaFile.writeText(
                            buildString {
                                append(manifest.readText().trim())
                                append('\n')
                                append("Maven-Artifact: ")
                                append(group)
                                append(':')
                                append(extensions.getByType(BasePluginExtension::class.java).archivesName.get())
                                append(':')
                                append(version)
                                append('\n')
                            }
                        )
                    }
                }
            }
        }
    }
}

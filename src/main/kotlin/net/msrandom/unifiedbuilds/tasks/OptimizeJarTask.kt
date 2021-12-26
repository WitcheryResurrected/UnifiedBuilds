package net.msrandom.unifiedbuilds.tasks

import net.msrandom.unifiedbuilds.tasks.ProjectJarArchive.Companion.setConventions
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import proguard.gradle.ProGuardTask

abstract class OptimizeJarTask : ProGuardTask(), ProjectJarArchive {
    abstract val config: RegularFileProperty
        @Internal get

    abstract val input: RegularFileProperty
        @Internal get

    abstract val owningProject: Property<Project>
        @Internal get

    abstract val classpath: ConfigurableFileCollection
        @Internal get

    init {
        apply {
            setConventions()
            archiveClassifier.convention("minified")
            injars(input)
            outjars(archiveFile)
            libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
            project.configurations.named("compileClasspath").takeIf(Provider<*>::isPresent)?.let {
                classpath.from(it)
            }
            libraryjars(classpath)

            keep(
                """
                public class * {
                    public *;
                }
                """.trimIndent()
            )

            keepattributes("RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations")

            dontnote()

            doFirst {
                val conf = owningProject.map { it.rootProject.layout.projectDirectory.file("proguard.conf") }
                config.orElse(conf)
                    .takeIf { it.get().asFile.exists() }
                    ?.let {
                        configuration(it)
                    }
            }
        }
    }
}

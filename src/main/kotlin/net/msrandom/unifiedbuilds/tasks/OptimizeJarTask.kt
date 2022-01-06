package net.msrandom.unifiedbuilds.tasks

import net.msrandom.unifiedbuilds.tasks.ProjectJarArchive.Companion.setConventions
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import proguard.gradle.ProGuardTask
import java.io.File

abstract class OptimizeJarTask : ProGuardTask(), ProjectJarArchive {
    abstract val configs: ConfigurableFileCollection
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
            configs.from(owningProject.map { it.layout.projectDirectory.file("proguard.conf") })

            injars(input)
            outjars(archiveFile)

            project.configurations.findByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)?.let { compileClasspath ->
                classpath.from(compileClasspath)
            }

            if (JavaVersion.current().isJava9Compatible) {
                libraryjars("${System.getProperty("java.home")}/jmods")
            } else {
                libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
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

            configuration(configs.filter(File::exists))
        }
    }
}

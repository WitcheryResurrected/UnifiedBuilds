package net.msrandom.unifiedbuilds.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import proguard.gradle.ProGuardTask

@CacheableTask
abstract class OptimizeJarTask : ProGuardTask() {
    abstract val config: RegularFileProperty
        @Optional
        @PathSensitive(PathSensitivity.RELATIVE)
        @InputFile
        get

    abstract val input: RegularFileProperty
        @PathSensitive(PathSensitivity.NONE)
        @InputFile
        get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val classpath: ConfigurableFileCollection
        @Optional
        @Classpath
        @InputFiles
        get

    init {
        apply {
            injars(input)
            outjars(output)
            libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
            project.configurations.named("compileClasspath").takeIf(Provider<*>::isPresent)?.let {
                classpath.from(it)
            }
            libraryjars(classpath.files)

            keep(
                """
                public class * {
                    public *;
                }
                """.trimIndent()
            )

            keepattributes("RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations")

            doFirst {
                val config = config.orElse(project.rootProject.layout.projectDirectory.file("proguard.conf"))
                if (config.get().asFile.exists()) {
                    configuration(config)
                }
            }
        }
    }
}

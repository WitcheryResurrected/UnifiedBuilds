package net.msrandom.unifiedbuilds.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar

interface ProjectJarArchive {
    val archiveFileName: Property<String>
        @Optional
        @Input
        get

    val archiveFile: RegularFileProperty
        @Internal get

    val destinationDirectory: DirectoryProperty
        @Internal get

    val archiveBaseName: Property<String>
        @Optional
        @Input
        get

    val archiveAppendix: Property<String>
        @Optional
        @Input
        get

    val archiveVersion: Property<String>
        @Optional
        @Input
        get

    val archiveExtension: Property<String>
        @Optional
        @Input
        get

    val archiveClassifier: Property<String>
        @Optional
        @Input
        get

    @Internal
    fun getProject(): Project

    companion object {
        fun ProjectJarArchive.setConventions() {
            archiveFileName.convention(
                getProject().provider {
                    // [baseName]-[appendix]-[version]-[classifier].[extension], copied from AbstractArchiveTask
                    var name = archiveBaseName.orNull.takeIf { !it.isNullOrEmpty() } ?: ""

                    fun maybe(prefix: String?, value: String?) = if (!value.isNullOrEmpty()) {
                        if (!prefix.isNullOrEmpty()) {
                            "-$value"
                        } else {
                            value
                        }
                    } else {
                        ""
                    }

                    name += maybe(name, archiveAppendix.orNull)
                    name += maybe(name, archiveVersion.get())
                    name += maybe(name, archiveClassifier.orNull)
                    val extension = archiveExtension.orNull
                    name += if (!extension.isNullOrEmpty()) ".$extension" else ""
                    name
                }
            )

            val base = getProject().extensions.getByType(BasePluginExtension::class.java)
            destinationDirectory.convention(base.libsDirectory)
            archiveFile.convention(destinationDirectory.file(archiveFileName))
            archiveVersion.convention(getProject().provider { getProject().version.toString() })
            archiveBaseName.convention(base.archivesName)
            archiveExtension.set(Jar.DEFAULT_EXTENSION)
        }
    }
}

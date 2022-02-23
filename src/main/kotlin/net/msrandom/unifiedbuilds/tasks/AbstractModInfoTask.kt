package net.msrandom.unifiedbuilds.tasks

import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

@CacheableTask
abstract class AbstractModInfoTask : DefaultTask() {
    abstract val moduleData: Property<UnifiedBuildsModuleExtension>
        @Internal get

    abstract val rootData: Property<UnifiedBuildsExtension>
        @Internal get

    abstract val destinationDirectory: DirectoryProperty
        @Optional
        @OutputDirectory
        get

    init {
        apply {
            destinationDirectory.convention(project.layout.buildDirectory.dir("generatedModInfo"))
        }
    }
}

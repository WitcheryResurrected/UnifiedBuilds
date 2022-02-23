package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.common.util.MojangLicenseHelper
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.forge.ModsTomlTask
import net.msrandom.unifiedbuilds.tasks.forge.RemapForgeArtifactTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.jvm.tasks.Jar

class Forge(name: String, loaderVersion: String) : AbstractForgePlatform(name, loaderVersion) {
    override val remapTaskType
        get() = RemapForgeArtifactTask::class.java

    override val Jar.input
        get() = (this as RemapForgeArtifactTask).input

    override val Jar.shade: ConfigurableFileCollection
        get() = (this as RemapForgeArtifactTask).shade

    override val modInfo = ModInfoData("createModsToml", ModsTomlTask::class, "META-INF")

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        val minecraft = project.extensions.getByType(UserDevExtension::class.java)

        project.afterEvaluate {
            MojangLicenseHelper.hide(it, "official", version)
        }
        minecraft.mappings("official", version)

        project.tasks.withType(Jar::class.java).matching { it !is RemapForgeArtifactTask }.all {
            it.manifest { manifest ->
                module.info.modId.onSet { modId ->
                    manifest.attributes(
                        mapOf(
                            "Implementation-Title" to modId,
                            "Implementation-Version" to root.extensions.getByType(UnifiedBuildsExtension::class.java).modVersion.get()
                        )
                    )
                }
            }
        }
    }
}

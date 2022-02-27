package net.msrandom.unifiedbuilds.platforms

import net.minecraftforge.gradle.userdev.UserDevExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsExtension
import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import net.msrandom.unifiedbuilds.tasks.forge.MCModInfoTask
import net.msrandom.unifiedbuilds.tasks.forge.RemapLegacyArtifactTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import wtf.gofancy.fancygradle.FancyExtension

class Legacy(name: String, loaderVersion: String) : AbstractForgePlatform(name, loaderVersion) {
    override val remapTaskType
        get() = RemapLegacyArtifactTask::class.java

    override val Jar.input
        get() = (this as RemapLegacyArtifactTask).input

    override val Jar.shade: ConfigurableFileCollection
        get() = (this as RemapLegacyArtifactTask).shade

    override val modInfo = ModInfoData("createMcModInfo", MCModInfoTask::class)

    private val Project.coremodExtension: CoremodExtension
        get() {
            extensions.findByType(CoremodExtension::class.java)?.let {
                return it
            }

            val extension = CoremodExtension(this)
            extensions.add("coremod", extension)
            return extension
        }

    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)

        val minecraft = project.extensions.getByType(UserDevExtension::class.java)

        val baseProject = base?.project ?: project

        project.repositories.add(
            project.repositories.maven { it.setUrl("https://maven.msrandom.net/repository/root") }
        )

        project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, "net.msrandom.resourcefixer:LegacyFGResourceFixer:1.2-5")

        project.extensions.getByType(FancyExtension::class.java).apply {
            patches {
                it.coremods
                it.asm
            }
        }

        minecraft.mappings("snapshot", "20180814-1.12")

        if (parent != null) {
            project.configurations.all { configuration ->
                configuration.resolutionStrategy {
                    it.eachDependency { dependency ->
                        if (dependency.requested.group == "net.minecraftforge" && dependency.requested.name == "mergetool") {
                            dependency.useVersion("0.2.3.3")
                        }
                    }
                }
            }
        }

        minecraft.runs.all { config ->
            if (config.isClient) {
                config.args("--tweakClass", "net.msrandom.resourcefixer.ResourceFixerTweaker")
            }

            baseProject.coremodExtension.value.onSet { plugin ->
                config.property("fml.coreMods.load", plugin)
            }
        }

        project.tasks.withType(Jar::class.java).matching { it !is RemapLegacyArtifactTask }.all {
            it.manifest { manifest ->
                if (!project.extension<SourceSetContainer>().getByName(SourceSet.MAIN_SOURCE_SET_NAME).accessTransformers.isEmpty) {
                    manifest.attributes(mapOf("FMLAT" to "accesstransformer.cfg"))
                }
            }

            if (base == null || base.project == project) {
                project.coremodExtension.value.onSet { plugin ->
                    it.manifest { manifest ->
                        manifest.attributes(
                            mapOf(
                                "FMLCorePluginContainsFMLMod" to true,
                                "FMLCorePlugin" to plugin
                            )
                        )
                    }
                }
            }
        }
    }

    class CoremodExtension(val project: Project) {
        val value = UnifiedBuildsExtension.ObservableProperty(String::class, project)
    }
}

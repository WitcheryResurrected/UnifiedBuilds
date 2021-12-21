package net.msrandom.unifiedbuilds.platforms

import net.msrandom.unifiedbuilds.UnifiedBuildsModuleExtension
import org.gradle.api.Project

class Forge(name: String, loaderVersion: String) : Platform(name, loaderVersion) {
    override fun handle(version: String, project: Project, root: Project, module: UnifiedBuildsModuleExtension, base: ProjectPlatform?, parent: Platform?) {
        super.handle(version, project, root, module, base, parent)
        // TODO
    }
}

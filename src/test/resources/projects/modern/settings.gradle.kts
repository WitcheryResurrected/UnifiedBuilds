rootProject.name = "test-mod-modern"

pluginManagement {
    includeBuild("../")
    repositories {
        maven(url = "https://files.minecraftforge.net/maven")
        maven(url = "https://gitlab.com/api/v4/projects/26758973/packages/maven")
        maven(url = "https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

fun includeMultiPlatform(name: String) {
    include(name)
    include("$name:forge")
    include("$name:fabric")
}

include("forge")
include("fabric")
includeMultiPlatform("test-base")
includeMultiPlatform("test-module")

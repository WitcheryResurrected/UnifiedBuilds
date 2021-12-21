rootProject.name = "test-mod-legacy"

pluginManagement {
    includeBuild("../")
    repositories {
        maven(url = "https://files.minecraftforge.net/maven")
        maven(url = "https://gitlab.com/api/v4/projects/26758973/packages/maven")
        maven(url = "https://maven.fabricmc.net/")
    }
}

include("test-base")
include("test-module")

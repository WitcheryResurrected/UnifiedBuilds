rootProject.name = "test-mod-modern"

fun includeMultiPlatform(name: String) {
    include(name)
    include("$name:forge")
    include("$name:fabric")
}

include("forge")
include("fabric")
includeMultiPlatform("test-base")
includeMultiPlatform("test-module")

rootProject.name = "test-mod-modern"

fun includeMultiPlatform(vararg names: String) {
    for (name in names)
        include(name, "$name:forge", "$name:fabric")
}

include("forge", "fabric")
includeMultiPlatform("test-base", "test-module")

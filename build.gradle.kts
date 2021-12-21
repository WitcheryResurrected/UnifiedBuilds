import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.6.+"
}

gradlePlugin {
    plugins.create("unifiedBuilds") {
        id = "net.msrandom.unifiedbuilds"
        version = "1.0"

        implementationClass = "net.msrandom.unifiedbuilds.UnifiedBuildsPlugin"
    }
}

val loomVersion = (findProperty("unifiedbuilds.fabric_loom.version") as? String) ?: "0.10-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://files.minecraftforge.net/maven")
    maven(url = "https://gitlab.com/api/v4/projects/26758973/packages/maven")
    maven(url = "https://maven.fabricmc.net/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(group = "com.electronwill.night-config", name = "core", version = "3.6.+")
    implementation(group = "com.electronwill.night-config", name = "toml", version = "3.6.+")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.+")

    implementation(group = "com.guardsquare", name = "proguard-gradle", version = "7.1.+")

    implementation(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = (findProperty("unifiedbuilds.forge_gradle.version") as? String) ?: "5.+")
    implementation(group = "wtf.gofancy.fancygradle", name = "wtf.gofancy.fancygradle.gradle.plugin", version = "1.+")
    implementation(group = "net.fabricmc", name = "fabric-loom", version = loomVersion)

    testImplementation(gradleTestKit())
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.8.+")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.8.+")
}

tasks.withType<KotlinCompile> {
    exclude {
        val endIndex = loomVersion.indexOf('.', 2).takeIf { i -> i != -1 } ?: loomVersion.indexOf('-', 2)
        val numberString = if (endIndex == -1) {
            loomVersion.substring(2)
        } else {
            loomVersion.substring(2, endIndex)
        }

        val version = numberString.toInt()

        var exclude = if (version >= 9) {
            it.name.contains("FabricLegacyMappingProvider")
        } else {
            it.name.contains("FabricModernMappingProvider")
        }

        if (version >= 8) {
            if (it.name.contains("FabricLegacyPluginApplier")) {
                exclude = true
            }
        } else if (it.name.contains("FabricModernPluginApplier")) {
            exclude = true
        }

        exclude
    }
}

tasks.withType<Test> {
    dependsOn("pluginUnderTestMetadata")
    useJUnitPlatform()
}

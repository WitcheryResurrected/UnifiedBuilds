import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.5.+"
}

val loomVersion = (findProperty("unifiedbuilds.fabric_loom.version") as? String) ?: "0.10-SNAPSHOT"
val pluginId = name

version = "0.2"
group = "net.msrandom"

System.getenv("GITHUB_RUN_NUMBER")?.let { version = "$version-$it" }

gradlePlugin {
    plugins.create("unifiedBuilds") {
        id = pluginId
        version = project.version

        implementationClass = "$group.$pluginId.UnifiedBuildsPlugin"
    }
}

repositories {
    mavenCentral()
    maven(url = "https://files.minecraftforge.net/maven")
    maven(url = "https://gitlab.com/api/v4/projects/26758973/packages/maven")
    maven(url = "https://maven.fabricmc.net/")
}

dependencies {
    implementation(kotlin("stdlib", version = "1.4.+"))

    implementation(group = "com.moandjiezana.toml", name = "toml4j", version = "0.7.+")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.+")
    implementation(group = "org.zeroturnaround", name = "zt-zip", version = "1.+")

    implementation(group = "com.guardsquare", name = "proguard-gradle", version = "7.1.+")

    implementation(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = (findProperty("unifiedbuilds.forge_gradle.version") as? String) ?: "5.+")
    implementation(group = "wtf.gofancy.fancygradle", name = "wtf.gofancy.fancygradle.gradle.plugin", version = "1.+")
    implementation(group = "net.fabricmc", name = "fabric-loom", version = loomVersion)

    testImplementation(gradleTestKit())
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.8.+")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.8.+")
}

tasks.withType<KotlinCompile> {
    // If we want lambdas, we have to use older versions that didn't apply optimizations that aren't compatible with gradle
    kotlinOptions.apiVersion = "1.4"
    kotlinOptions.languageVersion = "1.4"
}

tasks.compileKotlin {
    exclude {
        val endIndex = loomVersion.indexOf('.', 2).takeIf { i -> i != -1 } ?: loomVersion.indexOf('-', 2)
        val numberString = if (endIndex == -1) {
            loomVersion.substring(2)
        } else {
            loomVersion.substring(2, endIndex)
        }

        val version = numberString.toInt()

        if (version >= 9) {
            it.name.contains("FabricLegacyMappingProvider")
        } else {
            it.name.contains("FabricModernMappingProvider")
        }
    }
}

tasks.test {
    dependsOn("pluginUnderTestMetadata")
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.map(SourceSet::getAllSource))
}

artifacts {
    archives(sourcesJar)
}

publishing {
    System.getenv("MAVEN_USERNAME")?.let { mavenUsername ->
        System.getenv("MAVEN_PASSWORD")?.let { mavenPassword ->
            publications {
                create<MavenPublication>("maven") {
                    groupId = group.toString()
                    artifactId = pluginId
                    version = project.version.toString()

                    artifact(tasks.jar)
                    artifact(sourcesJar)
                }

                create<MavenPublication>("plugin") {
                    groupId = pluginId
                    artifactId = "$pluginId.gradle.plugin"
                    version = project.version.toString()

                    artifact(tasks.jar)
                }
            }

            repositories {
                maven {
                    url = uri("https://maven.msrandom.net/repository/root/")
                    credentials {
                        username = mavenUsername
                        password = mavenPassword
                    }
                }
            }
        }
    }
}

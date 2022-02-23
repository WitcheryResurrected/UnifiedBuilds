import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.5.+"
}

val pluginId = name

version = "0.7"
group = "net.msrandom"

System.getenv("GITHUB_RUN_NUMBER")?.let { version = "$version-$it" }

gradlePlugin {
    plugins.create("unifiedBuilds") {
        id = pluginId
        version = project.version

        implementationClass = "$group.$pluginId.UnifiedBuildsPlugin"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://files.minecraftforge.net/maven")
    maven(url = "https://gitlab.com/api/v4/projects/26758973/packages/maven")
    maven(url = "https://maven.fabricmc.net/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(group = "com.moandjiezana.toml", name = "toml4j", version = "0.7.+")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.+")

    implementation(group = "gradle.plugin.com.github.johnrengelman", name = "shadow", version = "7.1.+")

    implementation(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = "5.+")
    implementation(group = "wtf.gofancy.fancygradle", name = "wtf.gofancy.fancygradle.gradle.plugin", version = "1.+")
    implementation(group = "net.fabricmc", name = "fabric-loom", version = "0.11-SNAPSHOT")

    testImplementation(gradleTestKit())
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.8.+")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.8.+")
}

dependencies {
    runtimeOnly(files("libs"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.apiVersion = "1.5"
    kotlinOptions.languageVersion = "1.5"
}

tasks.test {
    dependsOn("pluginUnderTestMetadata")
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
    }
}

tasks.jar {
    from("LICENSE")
}

publishing {
    System.getenv("MAVEN_USERNAME")?.let { mavenUsername ->
        System.getenv("MAVEN_PASSWORD")?.let { mavenPassword ->
            publications {
                create<MavenPublication>("maven") {
                    groupId = group.toString()
                    artifactId = pluginId
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks.named("sourcesJar"))
                }

                create<MavenPublication>("plugin") {
                    groupId = pluginId
                    artifactId = "$pluginId.gradle.plugin"
                    version = project.version.toString()

                    from(components["java"])
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

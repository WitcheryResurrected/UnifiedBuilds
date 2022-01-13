import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.5.+"
}

val pluginId = name

version = "0.6"
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

    registerFeature("jdk8") {
        usingSourceSet(sourceSets.main.get())
    }

    registerFeature("jdk11") {
        usingSourceSet(sourceSets.main.get())
    }
}

configurations {
    named("jdk11ApiElements") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
        }
    }
    named("jdk11RuntimeElements") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
        }
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

    "jdk8Implementation"(group = "com.guardsquare", name = "proguard-gradle", version = "7.1.+")
    "jdk11Implementation"(group = "com.guardsquare", name = "proguard-gradle", version = "7.2.+")

    implementation(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = "5.+")
    implementation(group = "wtf.gofancy.fancygradle", name = "wtf.gofancy.fancygradle.gradle.plugin", version = "1.+")
    implementation(group = "net.fabricmc", name = "fabric-loom", version = "0.10-SNAPSHOT")

    testImplementation(gradleTestKit())
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.8.+")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.8.+")
}

tasks.withType<KotlinCompile> {
    // If we want lambdas, we have to use older versions that didn't apply optimizations that aren't compatible with gradle
    kotlinOptions.apiVersion = "1.4"
    kotlinOptions.languageVersion = "1.4"
    kotlinOptions.jvmTarget = "11"
}

tasks.test {
    dependsOn("pluginUnderTestMetadata")
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
    }
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
                    // artifact(sourcesJar)
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

package net.msrandom.unifiedbuilds

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import java.io.File

private fun test(name: String) {
    println("Building test project '$name'...")
    GradleRunner.create()
        .withProjectDir(File("src/test/resources/projects/$name"))
        .withPluginClasspath()
        .withArguments("build", "-s")
        .forwardOutput()
        .withDebug(true)
        .build()

    println("Built test project '$name' successfully.")
    println()
}

@Testable
class LegacyTest {
    @Test
    fun `Test Mods`() = test("legacy")
}

@Testable
class ModernTest {
    @Test
    fun `Test Mods`() = test("modern")
}

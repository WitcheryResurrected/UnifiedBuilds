package net.msrandom.unifiedbuilds

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import java.io.File

@Testable
class ProjectTests {
    @Test
    fun `Legacy Mod Test`() = test("legacy")

    @Test
    fun `Modern Mod Test`() = test("modern")

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
}

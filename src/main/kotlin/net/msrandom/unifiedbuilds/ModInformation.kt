package net.msrandom.unifiedbuilds

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

class ModInformation(val project: Project) : (ModInformation.() -> Unit) -> Unit {
    val modId = UnifiedBuildsExtension.ObservableProperty<String>(project)
    val name = property<String>()
    val description = property<String>()
    val authors = listProperty<String>()
    val contributors = listProperty<String>()
    val url = property<String>()
    val icon = property<String>()
    val mixins = listProperty<String>()
    val dependencies = listProperty<Dependency>()

    override fun invoke(info: ModInformation.() -> Unit) = info()

    private inline fun <reified T> property(): Property<T> = project.objects.property(T::class.java)
    private inline fun <reified T> listProperty(): ListProperty<T> = project.objects.listProperty(T::class.java)

    data class Dependency(val modId: String, val version: String? = null, val required: Boolean = true)
}

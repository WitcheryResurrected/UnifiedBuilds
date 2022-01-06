package net.msrandom.unifiedbuilds

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass

abstract class UnifiedBuildsExtension(private val project: Project) {
    val minecraftVersion = ObservableProperty<String>()

    /* The main project of the Jar */
    val baseProject = ObservableProperty<Project>()

    abstract val modVersion: Property<String>
    abstract val license: Property<String>

    /* The subprojects that would be included in the Jar */
    val modules: NamedDomainObjectContainer<Project> = project.container(Project::class.java)

    private inline fun <reified T : Any> ObservableProperty() = ObservableProperty(T::class, project)

    companion object {
        internal inline fun <reified T : Any> ObservableProperty(project: Project) = ObservableProperty(T::class, project)
    }

    class ObservableProperty<T : Any> internal constructor(
        type: KClass<T>,
        project: Project,
        private val property: Property<T> = project.objects.property(type.java)
    ) : Property<T> by property {
        var callback: ((T) -> Unit)? = null

        fun onSet(callback: (T) -> Unit) {
            this.callback = callback
            if (isPresent) {
                useCallback(get())
            }
        }

        override fun set(value: T?) {
            property.set(value)
            useCallback(value)
        }

        private fun useCallback(value: T?) {
            if (value != null) {
                callback?.let { it(value) }
            }
            callback = null
        }
    }
}

package net.msrandom.unifiedbuilds

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.Property
import kotlin.reflect.KClass

abstract class UnifiedBuildsExtension(private val project: Project) {
    val minecraftVersion = ObservableProperty<String>()

    /* The main project of the Jar */
    val baseProject = ObservableProperty<Project>()

    abstract val modVersion: Property<String>
    abstract val license: Property<String>

    /* The subprojects that would be included in the Jar */
    val modules: NamedDomainObjectContainer<Project> = project.container(Project::class.java)

    private inline fun <reified T : Any> ObservableProperty() = ObservableProperty<T>(project)

    companion object {
        internal inline fun <reified T : Any> ObservableProperty(project: Project) = ObservableProperty(T::class, project)
    }

    class ObservableProperty<T : Any>(
        type: KClass<T>,
        project: Project,
        private val property: Property<T> = project.objects.property(type.java)
    ) : Property<T> by property {
        private val callbacks = mutableListOf<(T) -> Unit>()

        internal fun onSet(callback: (T) -> Unit) {
            if (isPresent) {
                callback(get())
            } else {
                callbacks.add(callback)
            }
        }

        override fun set(value: T?) {
            property.set(value)
            value?.let {
                for (callback in callbacks) {
                    callback(value)
                }
                callbacks.clear()
            }
        }
    }
}

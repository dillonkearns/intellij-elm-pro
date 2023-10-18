package org.elm.ide.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

abstract class ElmProjectSettingsServiceBase<T : ElmProjectSettingsServiceBase.ElmProjectSettingsBase<T>>(
    val project: Project,
    state: T
) : SimplePersistentStateComponent<T>(state) {

    abstract class ElmProjectSettingsBase<T : ElmProjectSettingsBase<T>> : BaseState() {
        abstract fun copy(): T
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.PROPERTY)
    protected annotation class AffectsCargoMetadata

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.PROPERTY)
    protected annotation class AffectsHighlighting

    fun modify(action: (T) -> Unit) {
        val oldState = state.copy()
        val newState = state.also(action)
        val event = createSettingsChangedEvent(oldState, newState)
        notifySettingsChanged(event)
    }

    @TestOnly
    fun modifyTemporary(parentDisposable: Disposable, action: (T) -> Unit) {
        val oldState = state
        loadState(oldState.copy().also(action))
        Disposer.register(parentDisposable) {
            loadState(oldState)
        }
    }

    companion object {
        val ELM_SETTINGS_TOPIC: Topic<ElmSettingsListener> = Topic.create(
            "Elm settings changes",
            ElmSettingsListener::class.java,
            Topic.BroadcastDirection.TO_PARENT
        )
    }

    interface ElmSettingsListener {
        fun <T : ElmProjectSettingsBase<T>> settingsChanged(e: SettingsChangedEventBase<T>)
    }

    protected abstract fun createSettingsChangedEvent(oldEvent: T, newEvent: T): SettingsChangedEventBase<T>

    protected open fun notifySettingsChanged(event: SettingsChangedEventBase<T>) {
        project.messageBus.syncPublisher(ELM_SETTINGS_TOPIC).settingsChanged(event)

        if (event.affectsHighlighting) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    abstract class SettingsChangedEventBase<T : ElmProjectSettingsBase<T>>(val oldState: T, val newState: T) {
        private val cargoMetadataAffectingProps: List<KProperty1<T, *>> =
            oldState.javaClass.kotlin.memberProperties.filter { it.findAnnotation<AffectsCargoMetadata>() != null }

        private val highlightingAffectingProps: List<KProperty1<T, *>> =
            oldState.javaClass.kotlin.memberProperties.filter { it.findAnnotation<AffectsHighlighting>() != null }

        val affectsCargoMetadata: Boolean
            get() = cargoMetadataAffectingProps.any(::isChanged)

        val affectsHighlighting: Boolean
            get() = highlightingAffectingProps.any(::isChanged)

        /** Use it like `event.isChanged(State::foo)` to check whether `foo` property is changed or not */
        fun isChanged(prop: KProperty1<T, *>): Boolean = prop.get(oldState) != prop.get(newState)
    }
}

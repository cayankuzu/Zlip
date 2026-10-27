package com.zlip.app.ui

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lifecycle for Compose content in windows that are not activities (the cover presentation, the
 * screensaver). Borrowing an activity's lifecycle would tie that content to one activity instance.
 * Create on the main thread; call [destroy] when the window goes away.
 */
internal class ComposeHostOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** A ComposeView wired to this owner, disposed when its window detaches. */
    fun composeView(context: Context, content: @Composable () -> Unit): View = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@ComposeHostOwner)
        setViewTreeViewModelStoreOwner(this@ComposeHostOwner)
        setViewTreeSavedStateRegistryOwner(this@ComposeHostOwner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

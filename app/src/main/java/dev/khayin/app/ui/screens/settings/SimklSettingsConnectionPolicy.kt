package dev.khayin.app.ui.screens.settings

import dev.khayin.app.data.simkl.SimklAuthState
import dev.khayin.app.data.simkl.SimklConnectionMode

internal fun shouldRefreshMissingSimklIdentity(
    state: SimklAuthState,
    isRefreshBlocked: Boolean
): Boolean = state.isAuthenticated && state.username.isNullOrBlank() && !isRefreshBlocked

internal fun shouldCancelSimklPolling(mode: SimklConnectionMode): Boolean =
    mode == SimklConnectionMode.DISCONNECTED

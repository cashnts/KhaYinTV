package dev.khayin.app.ui.membership

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.khayin.app.domain.model.MemberAccess
import dev.khayin.app.domain.model.MemberTier

val LocalMemberAccess = staticCompositionLocalOf { MemberAccess.None }

object Membership {
    val access: MemberAccess
        @Composable
        @ReadOnlyComposable
        get() = LocalMemberAccess.current

    val tier: MemberTier?
        @Composable
        @ReadOnlyComposable
        get() = access.tier
}

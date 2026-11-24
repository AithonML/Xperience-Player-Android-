/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */
package com.xperience.player.ui.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue

enum class SearchSource {
    ONLINE,
    LOCAL
}

/**
 * Search input state shared between [AppFloatingNavBar]
 * and the search screens.
 */
@Immutable
data class NavSearchState(
    val visualActive: Boolean = false,
    val keyboardActive: Boolean = false,
    val query: TextFieldValue = TextFieldValue(),
    val onQueryChange: (TextFieldValue) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val searchSource: SearchSource = SearchSource.ONLINE,
    val onToggleSource: () -> Unit = {},
    val canToggleSource: Boolean = true,
    val onTapSearchIcon: () -> Unit = {},
    val onTapBar: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onCloseKeyboard: () -> Unit = {},
    val focusRequester: FocusRequester = FocusRequester(),
)

val LocalNavSearchState = staticCompositionLocalOf { NavSearchState() }

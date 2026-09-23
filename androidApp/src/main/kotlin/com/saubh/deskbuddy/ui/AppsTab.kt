@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Narrowest shortcut tile; the grid fits as many columns as the window allows. */
private val SHORTCUT_TILE_MIN_WIDTH = 104.dp
private const val APP_ROW_TILE_SPAN = 3

/** Shortcut tiles on top, searchable full list below, in one scrollable grid. */
@Composable
fun AppsTab(
    state: AppsUiState,
    onLaunch: (String) -> Unit,
    onPin: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.apps.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val refreshCd = stringResource(R.string.apps_refresh)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = SHORTCUT_TILE_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = fullSpan) {
            SectionTitle(stringResource(R.string.apps_shortcuts_title)) {
                FilledTonalIconButton(
                    onClick = onRefresh,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = refreshCd },
                ) { Icon(DeskBuddyIcons.Refresh, contentDescription = null) }
            }
        }
        if (state.shortcuts.isEmpty()) {
            item(span = fullSpan) { EmptyState(state.loaded, R.string.apps_no_shortcuts) }
        } else {
            items(state.shortcuts, key = { "s:" + it.id }) { app ->
                ShortcutTile(app, icon = state.icons[app.id]) { onLaunch(app.id) }
            }
        }

        item(span = fullSpan) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                SectionTitle(stringResource(R.string.apps_all_title))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                    leadingIcon = { Icon(DeskBuddyIcons.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (filtered.isEmpty()) {
            item(span = fullSpan) { EmptyState(state.loaded, R.string.apps_no_match) }
        } else {
            // A row is three tiles wide, so wide windows show several rows side by side.
            items(filtered, key = { "a:" + it.id }, span = { GridItemSpan(minOf(APP_ROW_TILE_SPAN, maxLineSpan)) }) { app ->
                AppRow(
                    app = app,
                    pinned = state.isShortcut(app.id),
                    onLaunch = { onLaunch(app.id) },
                    onTogglePin = { if (state.isShortcut(app.id)) onUnpin(app.id) else onPin(app.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(loaded: Boolean, messageRes: Int) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loaded) {
                Icon(DeskBuddyIcons.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            } else {
                LoadingIndicator(Modifier.size(40.dp))
            }
            Text(
                stringResource(if (loaded) messageRes else R.string.apps_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShortcutTile(app: DesktopApp, icon: ByteArray?, onLaunch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val cd = stringResource(R.string.cd_launch, app.name)
    Card(
        onClick = onLaunch,
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = Modifier.height(116.dp).semantics { contentDescription = cd },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            AppIcon(app.name, icon, 56.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
            Text(
                app.name,
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppRow(app: DesktopApp, pinned: Boolean, onLaunch: () -> Unit, onTogglePin: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val launchCd = stringResource(R.string.cd_launch, app.name)
    val pinCd = stringResource(if (pinned) R.string.cd_unpin else R.string.cd_pin)
    Card(
        onClick = onLaunch,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = launchCd },
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LetterAvatar(app.name, 40.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Text(
                app.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconToggleButton(
                checked = pinned,
                onCheckedChange = { onTogglePin() },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.semantics { contentDescription = pinCd },
            ) {
                Icon(
                    if (pinned) DeskBuddyIcons.Star else DeskBuddyIcons.StarOutline,
                    contentDescription = null,
                    tint = if (pinned) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

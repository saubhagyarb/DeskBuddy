package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Column that scrolls when its content outgrows the space, and stays vertically centred when it fits. */
@Composable
fun CenteredScrollColumn(
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else Dp.Unspecified
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

package com.saubh.deskbuddy.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.ShapeAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shell icon from the PC inside the badge shape, or the letter avatar when there is none. Decoded off the main thread. */
@Composable
fun AppIcon(name: String, icon: ByteArray?, size: Dp, shape: Shape, container: Color, onContainer: Color) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = icon) {
        value = icon?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    val decoded = bitmap
    if (decoded == null) {
        LetterAvatar(name, size, shape, container, onContainer)
    } else {
        ShapeAvatar(shape, container, size) {
            Image(decoded, contentDescription = null, modifier = Modifier.size(size * 0.62f))
        }
    }
}

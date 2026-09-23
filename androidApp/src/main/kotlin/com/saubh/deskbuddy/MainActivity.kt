package com.saubh.deskbuddy

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.saubh.deskbuddy.share.PendingShare
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.DeskBuddyApp

class MainActivity : ComponentActivity() {

    private val viewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeskBuddyApp(viewModel)
        }
        if (savedInstanceState == null) handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** System share sheet: ACTION_SEND with text or a single stream. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val stream = streamExtra(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when {
            stream != null -> viewModel.share(PendingShare.File(stream))
            !text.isNullOrBlank() -> viewModel.share(PendingShare.Text(text))
        }
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}

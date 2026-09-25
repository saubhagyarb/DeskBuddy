package com.saubh.deskbuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saubh.deskbuddy.service.ConnectionService
import com.saubh.deskbuddy.share.PendingShare
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.DeskBuddyApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ConnectionViewModel by viewModels()
    private var openedForAutoStandby = false

    /** Only affects whether the status / media notification is visible; the service runs either way. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeskBuddyApp(viewModel)
        }
        if (savedInstanceState == null) handleShareIntent(intent)
        openedForAutoStandby = intent?.getBooleanExtra("auto_standby", false) ?: false
        keepServiceInSyncWithPairedPcs()
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appGraph.autoStandby.isAutoStandby.collect { isAuto ->
                    if (!isAuto && openedForAutoStandby) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("auto_standby", false)) {
            openedForAutoStandby = true
        } else {
            openedForAutoStandby = false
        }
        handleShareIntent(intent)
    }

    /** The background service runs while at least one PC is paired (started here, while in the foreground). */
    private fun keepServiceInSyncWithPairedPcs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appGraph.prefs.saved.map { it.isNotEmpty() }.distinctUntilChanged().collect { paired ->
                    if (paired) {
                        ConnectionService.start(this@MainActivity)
                        askForNotifications()
                    } else {
                        ConnectionService.stop(this@MainActivity)
                    }
                }
            }
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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

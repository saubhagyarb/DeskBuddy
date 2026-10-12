package com.saubh.deskbuddy.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.saubh.deskbuddy.appGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wakes the app when charging starts while its process is gone (swiped away, killed for memory).
 * Android 8+ no longer delivers ACTION_POWER_CONNECTED to manifest receivers, so a job with a
 * charging constraint is the supported way in. It is one-shot and scheduled again on every unplug,
 * so it fires once per plug-in; [AutoStandbyManager] takes over once the service runs.
 */
class DockWakeJob : JobService() {

    companion object {
        private const val JOB_ID = 0x4B4E

        /** No-op while one is already pending. Persisted, so it survives a reboot. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, DockWakeJob::class.java))
                .setRequiresCharging(true)
                .setPersisted(true)
                .build()
            // A refused schedule only loses the wake-up from a dead process; the running service still docks.
            runCatching { scheduler.schedule(job) }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val graph = appGraph
        graph.scope.launch {
            val paired = graph.prefs.saved.first().isNotEmpty()
            // Android 12+ refuses a background service start unless battery optimisation is off; retry with backoff then.
            val started = !paired || runCatching { ConnectionService.start(this@DockWakeJob) }.isSuccess
            jobFinished(params, !started)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

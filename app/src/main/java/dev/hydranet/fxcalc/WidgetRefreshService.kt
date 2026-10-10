package dev.hydranet.fxcalc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetRefreshService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (ExchangeRatesWidget.widgetIds(this).isEmpty()) {
            cancel(this)
            return false
        }
        ExchangeRatesWidget.updateAll(this, refreshing = true)
        jobs[params.jobId] = scope.launch {
            try {
                withContext(Dispatchers.IO) { RatesRepository(this@WidgetRefreshService).fetch() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // The repository retains saved rates and records the failure for the widget.
            }
            jobs.remove(params.jobId)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        ExchangeRatesWidget.updateAll(this)
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PERIODIC_JOB_ID = 0x465801
        private const val REFRESH_JOB_ID = 0x465802
        private const val REFRESH_INTERVAL_MILLIS = 60 * 60 * 1000L

        internal fun schedulePeriodic(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, WidgetRefreshService::class.java))
                    .setPeriodic(REFRESH_INTERVAL_MILLIS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        internal fun refreshNow(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(REFRESH_JOB_ID, ComponentName(context, WidgetRefreshService::class.java))
                    .setOverrideDeadline(0)
                    .build(),
            )
        }

        internal fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).apply {
                cancel(PERIODIC_JOB_ID)
                cancel(REFRESH_JOB_ID)
            }
        }
    }
}

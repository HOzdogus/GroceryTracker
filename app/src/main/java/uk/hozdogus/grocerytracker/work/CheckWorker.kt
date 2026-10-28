package uk.hozdogus.grocerytracker.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import uk.hozdogus.grocerytracker.GroceryApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The daily price check, and its retries for items that couldn't be checked. Items are checked
 * stalest first, so if Android stops the run early the next one starts with the items it missed.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as GroceryApp).repository
        val retry = inputData.getInt(KEY_RETRY, 0)
        val dao = (applicationContext as GroceryApp).db.items()

        val all = dao.getAll().sortedBy { it.lastCheckedAt ?: 0L }
        val items = if (retry == 0) all else all.filter { !it.lastCheckOk }
        if (items.isEmpty()) return Result.success()

        val outcomes = repository.checkItems(items, recordRun = retry == 0)
        if (outcomes.any { !it.ok } && retry < MAX_RETRIES) scheduleRetry(applicationContext, retry + 1)
        return Result.success()
    }

    companion object {
        private const val KEY_RETRY = "retry"
        private const val DAILY = "daily-check"
        private const val MAX_RETRIES = 2
        private val CHECK_TIME: LocalTime = LocalTime.of(8, 0)

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Schedules the daily morning check. Safe to call on every app start: an existing schedule is kept. */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .setInitialDelay(untilNextCheck(ZonedDateTime.now(ZoneId.systemDefault())).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Retries failed items later: 2 hours after the first failure, then 4 hours after that. */
        private fun scheduleRetry(context: Context, attempt: Int) {
            val request = OneTimeWorkRequestBuilder<CheckWorker>()
                .setConstraints(network)
                .setInitialDelay(2L * attempt, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_RETRY to attempt))
                .build()
            // Each attempt has its own name, so a retry can schedule the next one while it is still running.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("retry-check-$attempt", ExistingWorkPolicy.REPLACE, request)
        }

        fun untilNextCheck(now: ZonedDateTime): Duration {
            var next = now.with(CHECK_TIME)
            if (!next.isAfter(now)) next = ZonedDateTime.of(LocalDate.from(now).plusDays(1), CHECK_TIME, now.zone)
            return Duration.between(now, next)
        }
    }
}

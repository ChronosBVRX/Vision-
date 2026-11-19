package com.chronosbvrx.visionplus.data.external

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Revisión periódica de las fichas que el usuario ya abrió. Cada 6 h renueva
 * como máximo 15 títulos, con red y sin batería crítica; una entrada fresca no
 * se vuelve a pedir. El resto del catálogo se completa al abrir cada ficha.
 */
internal class External4KWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val films = External4KStore.filmsForRefresh(app, limit = 15)
        if (films.isEmpty()) return Result.success()
        for (film in films) {
            if (isStopped) break
            if (External4KStore.checkedRecently(app, film.id)) continue
            External4KRepository.refreshNow(app, film)
            delay(400L)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "external-4k-refresh"

        fun ensure(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<External4KWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueue(androidx.work.OneTimeWorkRequestBuilder<External4KWorker>().build())
        }
    }
}

package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.JobFieldOpsCodec
import com.strobingn.wildlifefieldops.ai.fieldops.RouteStop
import com.strobingn.wildlifefieldops.ai.fieldops.RouteTrap
import com.strobingn.wildlifefieldops.ai.fieldops.TodayRouteEngine
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.TrapLogDao
import com.strobingn.wildlifefieldops.data.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TodayRouteViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val trapLogDao: TrapLogDao
) : ViewModel() {

    val jobs = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val traps = trapLogDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stops = combine(jobs, traps) { jobList, trapList ->
        val day = TodayRouteEngine.dayKey(System.currentTimeMillis(), ZoneId.systemDefault())
        val fromLogs = trapList.map { trap ->
            val parent = jobList.firstOrNull { it.id == trap.jobId }
            RouteTrap(
                id = trap.id,
                jobId = trap.jobId,
                label = trap.trapLocation.ifBlank { trap.trapId }.ifBlank { "Trap check" },
                address = parent?.address.orEmpty(),
                latitude = trap.latitude,
                longitude = trap.longitude,
                checkDate = trap.checkDate,
                nextCheckDate = trap.nextCheckDate
            )
        }
        val fromPricing = jobList.flatMap { job ->
            job.pricing.trapRecords.map { record ->
                RouteTrap(
                    id = record.id.ifBlank { record.trapId },
                    jobId = job.id,
                    label = record.trapLocation.ifBlank { record.trapId }.ifBlank { "Trap check" },
                    address = job.address,
                    latitude = record.latitude ?: job.latitude,
                    longitude = record.longitude ?: job.longitude,
                    checkDate = record.checkDate,
                    nextCheckDate = record.nextCheckDate
                )
            }
        }
        val dayStops = TodayRouteEngine.collect(jobList, (fromLogs + fromPricing).distinctBy { it.id }, day)
        TodayRouteEngine.order(dayStops, null, null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ordered(originLat: Double?, originLng: Double?, manual: List<RouteStop>?): List<RouteStop> {
        if (manual != null) return manual
        return TodayRouteEngine.order(stops.value, originLat, originLng)
    }

    fun persist(ordered: List<RouteStop>) = viewModelScope.launch {
        val day = TodayRouteEngine.dayKey(System.currentTimeMillis(), ZoneId.systemDefault())
        val current = jobDao.getAllOnce()
        TodayRouteEngine.applyOrder(current, ordered, day).forEach { job ->
            jobDao.insert(JobFieldOpsCodec.mergeForSave(job.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    fun clearManualOrder() = viewModelScope.launch {
        val day = TodayRouteEngine.dayKey(System.currentTimeMillis(), ZoneId.systemDefault())
        TodayRouteEngine.clearManual(jobDao.getAllOnce(), day).forEach { job ->
            jobDao.insert(JobFieldOpsCodec.mergeForSave(job.copy(updatedAt = System.currentTimeMillis())))
        }
    }
}

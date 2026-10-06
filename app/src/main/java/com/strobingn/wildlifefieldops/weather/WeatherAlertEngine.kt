package com.strobingn.wildlifefieldops.weather

import com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline
import com.strobingn.wildlifefieldops.data.model.JobStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns an hourly forecast plus NWS alerts into the next-48-hour warnings Sir
 * asked for. Pure: no network, no Android, so the thresholds can be unit tested.
 */
object WeatherAlertEngine {
    val ZONE: ZoneId = ZoneId.of("America/New_York")
    const val HORIZON_HOURS = 48L
    const val NEAR_MILES = 25.0
    const val EN_DASH = "\u2013"

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS
    private const val RUN_GAP_MS = 90 * 60 * 1000L

    private val severity = listOf(
        WeatherAlertKind.HEAVY_RAIN,
        WeatherAlertKind.HIGH_WIND,
        WeatherAlertKind.SNOW,
        WeatherAlertKind.RAIN
    )

    fun evaluate(
        hourly: List<HourlyWeather>,
        nws: List<NwsActiveAlert> = emptyList(),
        settings: WeatherAlertSettings = WeatherAlertSettings(),
        nowMillis: Long,
        zone: ZoneId = ZONE
    ): List<WeatherAlert> {
        val horizonEnd = nowMillis + HORIZON_HOURS * HOUR_MS
        val hours = hourly
            .filter { it.startMillis < horizonEnd && it.startMillis + HOUR_MS > nowMillis }
            .sortedBy { it.startMillis }
        val alerts = mutableListOf<WeatherAlert>()
        if (settings.rainEnabled) alerts += rainAlerts(hours, settings, zone)
        if (settings.heavyRainEnabled) {
            alerts += heavyHourAlerts(hours, settings, zone)
            alerts += heavyDayAlerts(hours, settings, zone)
            alerts += nwsAlerts(nws, nowMillis, horizonEnd, zone)
        }
        if (settings.highWindEnabled) alerts += windAlerts(hours, settings, zone)
        if (settings.snowEnabled) alerts += snowAlerts(hours, zone)
        return alerts.sortedWith(compareBy({ it.startMillis }, { severityIndex(it.kind) }))
    }

    fun unseen(alerts: List<WeatherAlert>, alreadyNotified: Set<String>): List<WeatherAlert> =
        alerts.filter { it.dedupeKey !in alreadyNotified }

    fun ordered(alerts: List<WeatherAlert>): List<WeatherAlert> =
        alerts.sortedWith(compareBy({ severityIndex(it.kind) }, { it.startMillis }))

    fun chipText(alerts: List<WeatherAlert>): String? {
        val top = ordered(alerts).firstOrNull() ?: return null
        val extra = alerts.size - 1
        return if (extra > 0) "${top.amountLabel} +$extra" else top.amountLabel
    }

    /**
     * Scheduled and In progress only. The job's calendar day in Eastern time
     * has to overlap the alert window.
     */
    fun jobWeatherAlerts(
        status: JobStatus,
        scheduledDateMillis: Long?,
        alerts: List<WeatherAlert>,
        zone: ZoneId = ZONE
    ): List<WeatherAlert> {
        val flag = JobStatusPipeline.flag(status)
        if (flag != JobStatus.SCHEDULED && flag != JobStatus.IN_PROGRESS) return emptyList()
        val date = scheduledDateMillis ?: return emptyList()
        return alerts.filter { overlapsJobDay(date, it, zone) }
    }

    fun alertsForJobLocation(
        status: JobStatus,
        scheduledDateMillis: Long?,
        jobLatitude: Double?,
        jobLongitude: Double?,
        primary: ForecastPoint?,
        otherPlaces: List<ForecastPoint> = emptyList(),
        zone: ZoneId = ZONE
    ): List<WeatherAlert> {
        val chosen = chooseForecast(jobLatitude, jobLongitude, primary, otherPlaces) ?: return emptyList()
        return jobWeatherAlerts(status, scheduledDateMillis, chosen.alerts, zone)
    }

    fun chooseForecast(
        jobLatitude: Double?,
        jobLongitude: Double?,
        primary: ForecastPoint?,
        otherPlaces: List<ForecastPoint> = emptyList()
    ): ForecastPoint? {
        val hasJobPoint = jobLatitude != null && jobLongitude != null &&
            jobLatitude.isFinite() && jobLongitude.isFinite() &&
            !(jobLatitude == 0.0 && jobLongitude == 0.0)
        if (!hasJobPoint) return primary
        val candidates = listOfNotNull(primary) + otherPlaces
        if (candidates.isEmpty()) return null
        val nearest = candidates.minBy { milesBetween(jobLatitude!!, jobLongitude!!, it.latitude, it.longitude) }
        val miles = milesBetween(jobLatitude!!, jobLongitude!!, nearest.latitude, nearest.longitude)
        return nearest.takeIf { miles <= NEAR_MILES }
    }

    fun milesBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthMiles = 3958.7613
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthMiles * asin(sqrt(a))
    }

    fun formatWindow(startMillis: Long, endMillis: Long, zone: ZoneId = ZONE): String {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        val sameDay = start.toLocalDate() == end.toLocalDate()
        val sameMeridiem = (start.hour < 12) == (end.hour < 12)
        val startDay = dayLabel(start)
        return when {
            sameDay && sameMeridiem ->
                "$startDay ${hourLabel(start, includeMeridiem = false)}$EN_DASH${hourLabel(end, includeMeridiem = true)}"
            sameDay ->
                "$startDay ${hourLabel(start, true)}$EN_DASH${hourLabel(end, true)}"
            else ->
                "$startDay ${hourLabel(start, true)}$EN_DASH${dayLabel(end)} ${hourLabel(end, true)}"
        }
    }

    fun formatLastChecked(millis: Long, zone: ZoneId = ZONE): String {
        val zdt = Instant.ofEpochMilli(millis).atZone(zone)
        val fmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
        return "Last checked ${fmt.format(zdt)}"
    }

    fun formatInches(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.').ifBlank { "0" }
    }

    fun formatMph(value: Double): Int = round(value).toInt()

    fun liquidInches(hour: HourlyWeather): Double = when {
        hour.rainInches > 0.0 -> hour.rainInches
        hour.snowfallInches > 0.0 -> 0.0
        else -> hour.precipitationInches
    }

    fun isFloodOrHeavyRain(event: String): Boolean {
        val text = event.lowercase(Locale.US)
        return "flood" in text || "heavy rain" in text
    }

    private fun rainAlerts(
        hours: List<HourlyWeather>,
        settings: WeatherAlertSettings,
        zone: ZoneId
    ): List<WeatherAlert> {
        val hits = hours.filter { it.precipitationProbability >= settings.rainProbabilityPercent }
        return consecutiveRuns(hits).map { run ->
            val peak = run.maxOf { it.precipitationProbability }
            val (start, end) = span(run)
            val window = formatWindow(start, end, zone)
            WeatherAlert(
                kind = WeatherAlertKind.RAIN,
                summary = "Rain likely $peak% $window",
                amountLabel = "Rain $peak%",
                startMillis = start,
                endMillis = end,
                dedupeKey = key(WeatherAlertKind.RAIN, start, end, peak.toString())
            )
        }
    }

    private fun heavyHourAlerts(
        hours: List<HourlyWeather>,
        settings: WeatherAlertSettings,
        zone: ZoneId
    ): List<WeatherAlert> {
        val hits = hours.filter { liquidInches(it) + 1e-9 >= settings.heavyRainHourInches }
        return consecutiveRuns(hits).map { run ->
            val peak = run.maxOf { liquidInches(it) }
            val inches = formatInches(peak)
            val (start, end) = span(run)
            val window = formatWindow(start, end, zone)
            WeatherAlert(
                kind = WeatherAlertKind.HEAVY_RAIN,
                summary = "Heavy rain $inches in $window",
                amountLabel = "Heavy rain $inches in",
                startMillis = start,
                endMillis = end,
                dedupeKey = key(WeatherAlertKind.HEAVY_RAIN, start, end, "hour:$inches")
            )
        }
    }

    private fun heavyDayAlerts(
        hours: List<HourlyWeather>,
        settings: WeatherAlertSettings,
        zone: ZoneId
    ): List<WeatherAlert> {
        if (hours.isEmpty()) return emptyList()
        val sorted = hours.sortedBy { it.startMillis }
        data class Window(val hours: List<HourlyWeather>, val sum: Double)
        val windows = mutableListOf<Window>()
        for (i in sorted.indices) {
            val start = sorted[i].startMillis
            val slice = sorted.filter { it.startMillis >= start && it.startMillis < start + DAY_MS }
            val sum = slice.sumOf { liquidInches(it) }
            val peak = slice.maxOf { liquidInches(it) }
            if (sum + 1e-6 >= settings.heavyRainDayInches && peak + 1e-6 < settings.heavyRainDayInches) {
                windows += Window(slice, sum)
            }
        }
        if (windows.isEmpty()) return emptyList()
        val flagged = windows.flatMap { it.hours }.map { it.startMillis }.toSet()
        val marked = sorted.filter { it.startMillis in flagged && liquidInches(it) > 0.0 }
        return consecutiveRuns(marked).map { run ->
            val (start, end) = span(run)
            val covering = windows.filter { window ->
                run.any { hour -> window.hours.any { it.startMillis == hour.startMillis } }
            }
            val inches = formatInches(covering.maxOf { it.sum })
            val window = formatWindow(start, end, zone)
            WeatherAlert(
                kind = WeatherAlertKind.HEAVY_RAIN,
                summary = "Heavy rain $inches in over 24 h $window",
                amountLabel = "Heavy rain $inches in",
                startMillis = start,
                endMillis = end,
                dedupeKey = key(WeatherAlertKind.HEAVY_RAIN, start, end, "day:$inches")
            )
        }
    }

    private fun windAlerts(
        hours: List<HourlyWeather>,
        settings: WeatherAlertSettings,
        zone: ZoneId
    ): List<WeatherAlert> {
        val limit = settings.windMphThreshold
        val hits = hours.filter { it.windSpeedMph > limit || it.windGustsMph > limit }
        return consecutiveRuns(hits).map { run ->
            val peakGust = run.maxOf { it.windGustsMph }
            val peakSustained = run.maxOf { it.windSpeedMph }
            val gustWins = peakGust > limit && peakGust >= peakSustained
            val mph = formatMph(if (gustWins) peakGust else peakSustained)
            val (start, end) = span(run)
            val window = formatWindow(start, end, zone)
            val summary = if (gustWins) "Gusts to $mph mph $window" else "Wind $mph mph $window"
            val amount = if (gustWins) "Gusts to $mph mph" else "Wind $mph mph"
            WeatherAlert(
                kind = WeatherAlertKind.HIGH_WIND,
                summary = summary,
                amountLabel = amount,
                startMillis = start,
                endMillis = end,
                dedupeKey = key(WeatherAlertKind.HIGH_WIND, start, end, mph.toString())
            )
        }
    }

    private fun snowAlerts(hours: List<HourlyWeather>, zone: ZoneId): List<WeatherAlert> {
        val hits = hours.filter { it.snowfallInches > 0.0 }
        return consecutiveRuns(hits).map { run ->
            val peak = run.maxOf { it.snowfallInches }
            val inches = formatInches(peak)
            val (start, end) = span(run)
            val window = formatWindow(start, end, zone)
            WeatherAlert(
                kind = WeatherAlertKind.SNOW,
                summary = "Snow $inches in $window",
                amountLabel = "Snow $inches in",
                startMillis = start,
                endMillis = end,
                dedupeKey = key(WeatherAlertKind.SNOW, start, end, inches)
            )
        }
    }

    private fun nwsAlerts(
        nws: List<NwsActiveAlert>,
        nowMillis: Long,
        horizonEnd: Long,
        zone: ZoneId
    ): List<WeatherAlert> {
        return nws.mapNotNull { alert ->
            if (!isFloodOrHeavyRain(alert.event)) return@mapNotNull null
            val onset = alert.onsetMillis ?: nowMillis
            val ends = alert.endsMillis ?: horizonEnd
            if (ends <= nowMillis || onset >= horizonEnd || ends <= onset) return@mapNotNull null
            val name = alert.event.trim().ifBlank { "Heavy rain" }
            val window = formatWindow(onset, ends, zone)
            WeatherAlert(
                kind = WeatherAlertKind.HEAVY_RAIN,
                summary = "$name $window",
                amountLabel = name,
                startMillis = onset,
                endMillis = ends,
                dedupeKey = "HEAVY_RAIN|nws|${alert.id.ifBlank { "$onset|$ends|$name" }}"
            )
        }
    }

    private fun overlapsJobDay(jobDateMillis: Long, alert: WeatherAlert, zone: ZoneId): Boolean {
        val jobDay = Instant.ofEpochMilli(jobDateMillis).atZone(zone).toLocalDate()
        val startDay = Instant.ofEpochMilli(alert.startMillis).atZone(zone).toLocalDate()
        val endInclusive = (alert.endMillis - 1).coerceAtLeast(alert.startMillis)
        val endDay = Instant.ofEpochMilli(endInclusive).atZone(zone).toLocalDate()
        return !jobDay.isBefore(startDay) && !jobDay.isAfter(endDay)
    }

    private fun consecutiveRuns(hours: List<HourlyWeather>): List<List<HourlyWeather>> {
        if (hours.isEmpty()) return emptyList()
        val sorted = hours.sortedBy { it.startMillis }
        val runs = mutableListOf<MutableList<HourlyWeather>>()
        var current = mutableListOf(sorted.first())
        for (hour in sorted.drop(1)) {
            if (hour.startMillis - current.last().startMillis <= RUN_GAP_MS) {
                current += hour
            } else {
                runs += current
                current = mutableListOf(hour)
            }
        }
        runs += current
        return runs
    }

    private fun span(run: List<HourlyWeather>): Pair<Long, Long> {
        val start = run.first().startMillis
        val end = run.last().startMillis + HOUR_MS
        return start to end
    }

    private fun key(kind: WeatherAlertKind, start: Long, end: Long, magnitude: String): String =
        "${kind.name}|$start|$end|$magnitude"

    private fun severityIndex(kind: WeatherAlertKind): Int = severity.indexOf(kind).let { if (it < 0) 99 else it }

    private fun dayLabel(time: ZonedDateTime): String =
        time.format(DateTimeFormatter.ofPattern("EEE", Locale.US))

    private fun hourLabel(time: ZonedDateTime, includeMeridiem: Boolean): String {
        val hour12 = time.hour % 12
        val hour = if (hour12 == 0) 12 else hour12
        if (!includeMeridiem) return hour.toString()
        val meridiem = if (time.hour < 12) "AM" else "PM"
        return "$hour $meridiem"
    }
}

data class ForecastPoint(
    val latitude: Double,
    val longitude: Double,
    val alerts: List<WeatherAlert>
)

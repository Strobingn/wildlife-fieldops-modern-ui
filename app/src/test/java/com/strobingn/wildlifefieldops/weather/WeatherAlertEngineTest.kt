package com.strobingn.wildlifefieldops.weather

import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeatherAlertEngineTest {

    @Test
    fun exactly30MphIsNotAnAlert() {
        val hours = listOf(hour("2026-10-07T14:00", wind = 30.0, gust = 30.0))
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T14:00"))
        assertTrue(alerts.none { it.kind == WeatherAlertKind.HIGH_WIND })
    }

    @Test
    fun steadyWindAloneDoesNotAlert() {
        val hours = listOf(hour("2026-10-07T14:00", wind = 40.0, gust = 30.0))
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T14:00"))
        assertTrue(alerts.none { it.kind == WeatherAlertKind.HIGH_WIND })
    }

    @Test
    fun thirtyOneMphGustShowsSteadyWindBesideIt() {
        val hours = listOf(hour("2026-10-07T14:00", wind = 18.0, gust = 31.0))
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T14:00"))
        assertEquals("Gusts 31 mph, steady 18 mph, Wed 2–3 PM", alerts.single().summary)
        assertEquals(WeatherAlertKind.HIGH_WIND, alerts.single().kind)
    }

    @Test
    fun gustsUseTheSteadySpeedFromThePeakGustHour() {
        val hours = (14..17).map { clock ->
            val steady = when (clock) {
                14 -> 25.0
                15 -> 18.0
                else -> 12.0
            }
            hour(
                "2026-10-07T$clock:00",
                wind = steady,
                gust = if (clock == 15) 38.0 else 32.0
            )
        }
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T12:00"))
        assertEquals("Gusts 38 mph, steady 18 mph, Wed 2–6 PM", alerts.single().summary)
        assertEquals("Gusts 38 mph, steady 18 mph", alerts.single().amountLabel)
    }

    @Test
    fun rainUses50PercentAndIgnores49() {
        val quiet = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T16:00", pop = 49)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertTrue(quiet.none { it.kind == WeatherAlertKind.RAIN })
        val likely = WeatherAlertEngine.evaluate(
            listOf(
                hour("2026-10-07T16:00", pop = 50),
                hour("2026-10-07T17:00", pop = 70)
            ),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertEquals("Rain likely 70% Wed 4–6 PM", likely.single().summary)
    }

    @Test
    fun heavyRainHourStartsAtPoint30Inches() {
        val under = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T18:00", rain = 0.29)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertTrue(under.none { it.kind == WeatherAlertKind.HEAVY_RAIN })
        val hit = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T18:00", rain = 0.30)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertEquals("Heavy rain 0.3 in Wed 6–7 PM", hit.single().summary)
    }

    @Test
    fun heavyRainDayStartsAtOneInchWithoutASingleHeavyHour() {
        val light = (0..6).map { offset ->
            hour("2026-10-07T${12 + offset}:00", rain = 0.125)
        }
        val under = WeatherAlertEngine.evaluate(light, nowMillis = nowAt("2026-10-07T12:00"))
        assertTrue(under.none { it.summary.contains("over 24 h") })

        val soaking = (0..3).map { offset ->
            hour("2026-10-07T${12 + offset}:00", rain = 0.25)
        }
        val alerts = WeatherAlertEngine.evaluate(soaking, nowMillis = nowAt("2026-10-07T12:00"))
        assertEquals("Heavy rain 1 in over 24 h Wed 12–4 PM", alerts.single().summary)
    }

    @Test
    fun aSingleHourOverOneInchDoesNotAlsoRaiseTheDayAlert() {
        val alerts = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T18:00", rain = 1.2)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertEquals(1, alerts.size)
        assertEquals("Heavy rain 1.2 in Wed 6–7 PM", alerts.single().summary)
    }

    @Test
    fun snowAlertsOnAnySnowfall() {
        val none = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T20:00", snow = 0.0)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertTrue(none.none { it.kind == WeatherAlertKind.SNOW })
        val some = WeatherAlertEngine.evaluate(
            listOf(
                hour("2026-10-07T20:00", snow = 0.4),
                hour("2026-10-07T21:00", snow = 0.1)
            ),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        assertEquals("Snow 0.4 in Wed 8–10 PM", some.single().summary)
    }

    @Test
    fun officialNwsWarningWatchAndAdvisoryShowHeadlineAndWindow() {
        val now = nowAt("2026-10-07T12:00")
        val products = listOf(
            NwsActiveAlert(
                id = "flood-1",
                event = "Flood Warning",
                headline = "Flood Warning issued October 7 at 12:00PM EDT",
                onsetMillis = now,
                endsMillis = now + 6 * 3_600_000L
            ),
            NwsActiveAlert(
                id = "watch-1",
                event = "Severe Thunderstorm Watch",
                headline = "Severe Thunderstorm Watch 412",
                onsetMillis = nowAt("2026-10-07T14:00"),
                endsMillis = nowAt("2026-10-07T18:00")
            ),
            NwsActiveAlert(
                id = "wind-1",
                event = "Wind Advisory",
                headline = "Wind Advisory in effect until 6 PM EDT",
                onsetMillis = nowAt("2026-10-07T14:00"),
                endsMillis = nowAt("2026-10-07T18:00")
            ),
            NwsActiveAlert(
                id = "statement-1",
                event = "Special Weather Statement",
                headline = "Special Weather Statement",
                onsetMillis = now,
                endsMillis = now + 3_600_000L
            )
        )
        val alerts = WeatherAlertEngine.evaluate(
            emptyList(),
            nws = products,
            settings = WeatherAlertSettings(heavyRainEnabled = false, highWindEnabled = false),
            nowMillis = now
        )
        assertEquals(3, alerts.size)
        assertTrue(alerts.all { it.kind == WeatherAlertKind.NWS })
        assertTrue(alerts.all { it.summary.startsWith("Official NWS alert:") })
        assertEquals(
            "Official NWS alert: Flood Warning issued October 7 at 12:00PM EDT, Wed 12–6 PM",
            alerts.first { it.dedupeKey.endsWith("flood-1") }.summary
        )
        assertEquals(
            "Official NWS alert: Severe Thunderstorm Watch 412, Wed 2–6 PM",
            alerts.first { it.dedupeKey.endsWith("watch-1") }.summary
        )
        assertEquals(
            "Official NWS alert: Wind Advisory in effect until 6 PM EDT, Wed 2–6 PM",
            alerts.first { it.dedupeKey.endsWith("wind-1") }.summary
        )
        assertTrue(alerts.none { it.summary.contains("Special Weather Statement") })
        val onTheJob = WeatherAlertEngine.jobWeatherAlerts(JobStatus.SCHEDULED, now, alerts)
        assertEquals(3, onTheJob.size)
        assertEquals(onTheJob.first().summary, WeatherAlertEngine.chipText(listOf(onTheJob.first())))
    }

    @Test
    fun eachToggleCanSilenceItsType() {
        val hours = listOf(
            hour("2026-10-07T14:00", pop = 80, rain = 0.4, snow = 0.2, wind = 40.0, gust = 50.0)
        )
        val now = nowAt("2026-10-07T14:00")
        val all = WeatherAlertEngine.evaluate(hours, nowMillis = now)
        assertEquals(
            setOf(
                WeatherAlertKind.RAIN,
                WeatherAlertKind.HEAVY_RAIN,
                WeatherAlertKind.HIGH_WIND,
                WeatherAlertKind.SNOW
            ),
            all.map { it.kind }.toSet()
        )
        assertTrue(
            WeatherAlertEngine.evaluate(hours, settings = WeatherAlertSettings(rainEnabled = false), nowMillis = now)
                .none { it.kind == WeatherAlertKind.RAIN }
        )
        assertTrue(
            WeatherAlertEngine.evaluate(hours, settings = WeatherAlertSettings(heavyRainEnabled = false), nowMillis = now)
                .none { it.kind == WeatherAlertKind.HEAVY_RAIN }
        )
        assertTrue(
            WeatherAlertEngine.evaluate(hours, settings = WeatherAlertSettings(highWindEnabled = false), nowMillis = now)
                .none { it.kind == WeatherAlertKind.HIGH_WIND }
        )
        assertTrue(
            WeatherAlertEngine.evaluate(hours, settings = WeatherAlertSettings(snowEnabled = false), nowMillis = now)
                .none { it.kind == WeatherAlertKind.SNOW }
        )
    }

    @Test
    fun dedupeDropsTheSameAlertAndKeepsAStrongerOne() {
        val first = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T14:00", gust = 38.0)),
            nowMillis = nowAt("2026-10-07T14:00")
        )
        val keys = first.map { it.dedupeKey }.toSet()
        assertEquals(first.map { it.dedupeKey }, WeatherAlertEngine.unseen(first, emptySet()).map { it.dedupeKey })
        assertTrue(WeatherAlertEngine.unseen(first, keys).isEmpty())
        val louder = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T14:00", gust = 45.0)),
            nowMillis = nowAt("2026-10-07T14:00")
        )
        assertEquals("Gusts 45 mph, steady 5 mph, Wed 2–3 PM", louder.single().summary)
        assertEquals(1, WeatherAlertEngine.unseen(louder, keys).size)
        assertFalse(louder.single().dedupeKey in keys)
    }

    @Test
    fun hoursOutsideTheNext48HoursAreIgnored() {
        val start = nowAt("2026-10-09T14:00")
        val gust = hour("2026-10-09T14:00", gust = 40.0)
        assertTrue(WeatherAlertEngine.evaluate(listOf(gust), nowMillis = start).isNotEmpty())
        val tooLate = start - 48L * 3_600_000L
        assertTrue(WeatherAlertEngine.evaluate(listOf(gust), nowMillis = tooLate).isEmpty())
        val stillInside = start - 47L * 3_600_000L
        assertTrue(WeatherAlertEngine.evaluate(listOf(gust), nowMillis = stillInside).isNotEmpty())
    }

    @Test
    fun jobChipFollowsScheduledAndInProgressOnTheAlertDay() {
        val alerts = WeatherAlertEngine.evaluate(
            listOf(hour("2026-10-07T14:00", wind = 18.0, gust = 38.0)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        val wednesday = nowAt("2026-10-07T09:00")
        val friday = nowAt("2026-10-09T09:00")
        assertEquals("Gusts 38 mph, steady 18 mph", WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.SCHEDULED, wednesday, alerts)
        ))
        assertEquals("Gusts 38 mph, steady 18 mph", WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.IN_PROGRESS, wednesday, alerts)
        ))
        assertEquals("Gusts 38 mph, steady 18 mph", WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.TRAPPING, wednesday, alerts)
        ))
        assertNull(WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.COMPLETED, wednesday, alerts)
        ))
        assertNull(WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.CANCELLED, wednesday, alerts)
        ))
        assertNull(WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.SCHEDULED, friday, alerts)
        ))
        assertNull(WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.SCHEDULED, null, alerts)
        ))
    }

    @Test
    fun jobWithoutCoordinatesUsesThePrimaryForecast() {
        val primary = ForecastPoint(41.44, -74.02, listOf(sampleAlert()))
        val chosen = WeatherAlertEngine.chooseForecast(null, null, primary)
        assertEquals(primary, chosen)
        val nearby = WeatherAlertEngine.chooseForecast(41.46, -74.03, primary)
        assertEquals(primary, nearby)
        assertNull(WeatherAlertEngine.chooseForecast(43.10, -74.02, primary))
    }

    @Test
    fun openMeteoUrlAsksForImperialHoursAndNoKey() {
        val url = WeatherForecastUrls.openMeteo(41.44, -74.02)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(url.contains("hourly=precipitation,precipitation_probability,rain,snowfall,wind_speed_10m,wind_gusts_10m,weather_code"))
        assertTrue(url.contains("temperature_unit=fahrenheit"))
        assertTrue(url.contains("wind_speed_unit=mph"))
        assertTrue(url.contains("precipitation_unit=inch"))
        assertTrue(url.contains("timezone=America%2FNew_York"))
        assertFalse(url.contains("appid"))
        assertFalse(url.contains("api_key"))
        assertFalse(url.contains("apikey"))
        val nws = WeatherForecastUrls.nwsActive(41.4412, -74.0219)
        assertEquals("https://api.weather.gov/alerts/active?point=41.4412,-74.0219", nws)
        assertTrue(WeatherForecastUrls.USER_AGENT.contains("@"))
    }

    private fun sampleAlert(): WeatherAlert = WeatherAlert(
        kind = WeatherAlertKind.HIGH_WIND,
        summary = "Gusts 38 mph, steady 18 mph, Wed 2–6 PM",
        amountLabel = "Gusts 38 mph, steady 18 mph",
        startMillis = 1L,
        endMillis = 2L,
        dedupeKey = "sample"
    )

    private fun hour(
        local: String,
        pop: Int = 0,
        rain: Double = 0.0,
        snow: Double = 0.0,
        wind: Double = 5.0,
        gust: Double = 5.0
    ): HourlyWeather = HourlyWeather(
        startMillis = nowAt(local),
        precipitationInches = rain,
        precipitationProbability = pop,
        rainInches = rain,
        snowfallInches = snow,
        windSpeedMph = wind,
        windGustsMph = gust
    )

    private fun nowAt(local: String): Long =
        LocalDateTime.parse(local).atZone(WeatherAlertEngine.ZONE).toInstant().toEpochMilli()
}

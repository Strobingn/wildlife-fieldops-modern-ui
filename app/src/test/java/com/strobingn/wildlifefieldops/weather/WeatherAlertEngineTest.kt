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
    fun thirtyOneMphSustainedIsAnAlert() {
        val hours = listOf(hour("2026-10-07T14:00", wind = 31.0, gust = 10.0))
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T14:00"))
        assertEquals("Wind 31 mph Wed 2–3 PM", alerts.single().summary)
        assertEquals(WeatherAlertKind.HIGH_WIND, alerts.single().kind)
    }

    @Test
    fun thirtyOneMphGustIsAnAlert() {
        val hours = listOf(hour("2026-10-07T14:00", wind = 10.0, gust = 31.0))
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T14:00"))
        assertEquals("Gusts to 31 mph Wed 2–3 PM", alerts.single().summary)
    }

    @Test
    fun gustsTo38MphUseTheExampleWindow() {
        val hours = (14..17).map { clock ->
            hour(
                "2026-10-07T$clock:00",
                wind = 12.0,
                gust = if (clock == 15) 38.0 else 32.0
            )
        }
        val alerts = WeatherAlertEngine.evaluate(hours, nowMillis = nowAt("2026-10-07T12:00"))
        assertEquals("Gusts to 38 mph Wed 2–6 PM", alerts.single().summary)
        assertEquals("Gusts to 38 mph", alerts.single().amountLabel)
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
    fun nwsFloodAndHeavyRainCountAsHeavyRain() {
        val now = nowAt("2026-10-07T12:00")
        val flood = NwsActiveAlert(
            id = "flood-1",
            event = "Flood Warning",
            onsetMillis = now,
            endsMillis = now + 6 * 3_600_000L
        )
        val alerts = WeatherAlertEngine.evaluate(emptyList(), nws = listOf(flood), nowMillis = now)
        assertTrue(alerts.single().summary.startsWith("Flood Warning"))
        assertEquals(WeatherAlertKind.HEAVY_RAIN, alerts.single().kind)

        val windAdvisory = NwsActiveAlert(
            id = "wind-1",
            event = "Wind Advisory",
            onsetMillis = now,
            endsMillis = now + 3_600_000L
        )
        val ignored = WeatherAlertEngine.evaluate(emptyList(), nws = listOf(windAdvisory), nowMillis = now)
        assertTrue(ignored.isEmpty())
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
        assertEquals("Gusts to 45 mph Wed 2–3 PM", louder.single().summary)
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
            listOf(hour("2026-10-07T14:00", gust = 38.0)),
            nowMillis = nowAt("2026-10-07T12:00")
        )
        val wednesday = nowAt("2026-10-07T09:00")
        val friday = nowAt("2026-10-09T09:00")
        assertEquals("Gusts to 38 mph", WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.SCHEDULED, wednesday, alerts)
        ))
        assertEquals("Gusts to 38 mph", WeatherAlertEngine.chipText(
            WeatherAlertEngine.jobWeatherAlerts(JobStatus.IN_PROGRESS, wednesday, alerts)
        ))
        assertEquals("Gusts to 38 mph", WeatherAlertEngine.chipText(
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
        summary = "Gusts to 38 mph Wed 2–6 PM",
        amountLabel = "Gusts to 38 mph",
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

package com.strobingn.wildlifefieldops.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OpenMeteoParserTest {

    @Test
    fun parsesSavedSampleAndBuildsEachAlert() {
        val body = javaClass.classLoader!!.getResourceAsStream("open-meteo-sample.json")!!
            .bufferedReader()
            .use { it.readText() }
        val hourly = OpenMeteoParser.parse(body)
        assertEquals(9, hourly.size)
        assertEquals(30.0, hourly.first().windSpeedMph, 0.001)
        assertEquals(30.0, hourly.first().windGustsMph, 0.001)
        assertEquals(49, hourly.first().precipitationProbability)
        assertEquals(38.0, hourly[2].windGustsMph, 0.001)
        assertEquals(0.4, hourly[6].rainInches, 0.001)
        assertEquals(65, hourly[6].weatherCode)
        assertEquals(0.25, hourly[7].snowfallInches, 0.001)

        val now = LocalDateTime.parse("2026-10-07T12:00")
            .atZone(WeatherAlertEngine.ZONE)
            .toInstant()
            .toEpochMilli()
        val alerts = WeatherAlertEngine.evaluate(hourly, nowMillis = now)
        val summaries = alerts.map { it.summary }.toSet()
        assertEquals(
            setOf(
                "Gusts 38 mph, steady 12 mph, Wed 2–3 PM",
                "Rain likely 80% Wed 4–7 PM",
                "Heavy rain 0.4 in Wed 6–7 PM",
                "Snow 0.25 in Wed 8–10 PM"
            ),
            summaries
        )
        assertTrue(alerts.none { it.summary.contains("30 mph") })

        val keys = alerts.map { it.dedupeKey }.toSet()
        assertEquals(alerts.size, keys.size)
        assertTrue(WeatherAlertEngine.unseen(alerts, keys).isEmpty())
        assertEquals(alerts.size, WeatherAlertEngine.unseen(alerts, emptySet()).size)
    }

    @Test
    fun parsesNwsActiveAlerts() {
        val body = """
            {
              "features": [
                {
                  "id": "https://api.weather.gov/alerts/urn:oid:2.49.0.1.840.0.example",
                  "properties": {
                    "event": "Flash Flood Warning",
                    "headline": "Flash Flood Warning issued October 7",
                    "onset": "2026-10-07T18:00:00-04:00",
                    "ends": "2026-10-08T00:00:00-04:00"
                  }
                },
                {
                  "id": "https://api.weather.gov/alerts/fog",
                  "properties": {
                    "event": "Dense Fog Advisory",
                    "onset": "2026-10-07T18:00:00-04:00",
                    "ends": "2026-10-07T22:00:00-04:00"
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = NwsAlertParser.parse(body)
        assertEquals(2, parsed.size)
        assertEquals("Flash Flood Warning", parsed[0].event)
        assertTrue(parsed[0].id.contains("example"))
        val now = LocalDateTime.parse("2026-10-07T12:00")
            .atZone(WeatherAlertEngine.ZONE)
            .toInstant()
            .toEpochMilli()
        val alerts = WeatherAlertEngine.evaluate(emptyList(), nws = parsed, nowMillis = now)
        assertEquals(
            setOf(
                "Official NWS alert: Flash Flood Warning issued October 7, Wed 6 PM–Thu 12 AM",
                "Official NWS alert: Dense Fog Advisory, Wed 6–10 PM"
            ),
            alerts.map { it.summary }.toSet()
        )
        assertTrue(alerts.all { it.kind == WeatherAlertKind.NWS })
        assertTrue(WeatherAlertEngine.unseen(alerts, alerts.map { it.dedupeKey }.toSet()).isEmpty())
    }

    @Test
    fun blankAndBrokenPayloadsDoNotThrow() {
        assertTrue(OpenMeteoParser.parse("").isEmpty())
        assertTrue(OpenMeteoParser.parse("{}").isEmpty())
        assertTrue(NwsAlertParser.parse("not-json").isEmpty())
        assertTrue(NwsAlertParser.parse("{\"features\":[]}").isEmpty())
    }
}

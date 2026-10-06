package com.strobingn.wildlifefieldops.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId

object OpenMeteoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String, zone: ZoneId = WeatherAlertEngine.ZONE): List<HourlyWeather> {
        if (body.isBlank()) return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val hourly = root["hourly"]?.jsonObject ?: return emptyList()
        val times = hourly["time"]?.jsonArray ?: return emptyList()
        val precipitation = hourly["precipitation"]?.jsonArray
        val probability = hourly["precipitation_probability"]?.jsonArray
        val rain = hourly["rain"]?.jsonArray
        val snowfall = hourly["snowfall"]?.jsonArray
        val wind = hourly["wind_speed_10m"]?.jsonArray
        val gusts = hourly["wind_gusts_10m"]?.jsonArray
        val codes = hourly["weather_code"]?.jsonArray
        return times.mapIndexedNotNull { index, element ->
            val text = element.asText() ?: return@mapIndexedNotNull null
            val start = runCatching {
                LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: return@mapIndexedNotNull null
            HourlyWeather(
                startMillis = start,
                precipitationInches = precipitation.numberAt(index),
                precipitationProbability = probability.intAt(index),
                rainInches = rain.numberAt(index),
                snowfallInches = snowfall.numberAt(index),
                windSpeedMph = wind.numberAt(index),
                windGustsMph = gusts.numberAt(index),
                weatherCode = codes.intAt(index)
            )
        }
    }

    private fun JsonElement.asText(): String? {
        if (this is JsonNull) return null
        return jsonPrimitive.content.takeIf { it.isNotBlank() }
    }

    private fun JsonArray?.numberAt(index: Int): Double {
        val element = this?.getOrNull(index) ?: return 0.0
        if (element is JsonNull) return 0.0
        return element.jsonPrimitive.content.toDoubleOrNull() ?: 0.0
    }

    private fun JsonArray?.intAt(index: Int): Int {
        val element = this?.getOrNull(index) ?: return 0
        if (element is JsonNull) return 0
        return element.jsonPrimitive.content.toIntOrNull()
            ?: element.jsonPrimitive.content.toDoubleOrNull()?.toInt()
            ?: 0
    }
}

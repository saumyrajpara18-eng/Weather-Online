package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Network DTOs for Open-Meteo ---

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "results") val results: List<GeocodingResult>? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResult(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "country") val country: String? = null,
    @Json(name = "admin1") val admin1: String? = null,
    @Json(name = "country_code") val countryCode: String? = null
)

@JsonClass(generateAdapter = true)
data class ForecastResponse(
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "current") val current: CurrentDto? = null,
    @Json(name = "daily") val daily: DailyDto? = null
)

@JsonClass(generateAdapter = true)
data class CurrentDto(
    @Json(name = "time") val time: String? = null,
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    @Json(name = "is_day") val isDay: Int? = null,
    @Json(name = "precipitation") val precipitation: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailyDto(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int>? = null,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>? = null,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null
)

// --- Domain Models ---

data class City(
    val name: String,
    val country: String,
    val admin1: String? = null,
    val latitude: Double,
    val longitude: Double
) {
    val displayLocation: String
        get() = if (!admin1.isNullOrBlank() && admin1 != name) {
            "$name, $admin1"
        } else if (country.isNotBlank()) {
            "$name, $country"
        } else {
            name
        }
}

enum class TemperatureUnit(val symbol: String, val apiValue: String) {
    CELSIUS("°C", "celsius"),
    FAHRENHEIT("°F", "fahrenheit")
}

data class WeatherCondition(
    val description: String,
    val icon: ImageVector,
    val isDay: Boolean = true,
    val backgroundGradient: List<Color>
)

object WeatherCodeMapper {
    fun getCondition(weatherCode: Int, isDay: Boolean = true): WeatherCondition {
        return when (weatherCode) {
            0 -> if (isDay) {
                WeatherCondition(
                    description = "Clear Sky",
                    icon = Icons.Rounded.WbSunny,
                    isDay = true,
                    backgroundGradient = listOf(Color(0xFF2563EB), Color(0xFF38BDF8), Color(0xFFFDE047))
                )
            } else {
                WeatherCondition(
                    description = "Clear Night",
                    icon = Icons.Rounded.NightsStay,
                    isDay = false,
                    backgroundGradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF312E81))
                )
            }
            1, 2 -> if (isDay) {
                WeatherCondition(
                    description = if (weatherCode == 1) "Mainly Clear" else "Partly Cloudy",
                    icon = Icons.Rounded.WbCloudy,
                    isDay = true,
                    backgroundGradient = listOf(Color(0xFF1E40AF), Color(0xFF60A5FA), Color(0xFF93C5FD))
                )
            } else {
                WeatherCondition(
                    description = "Partly Cloudy",
                    icon = Icons.Rounded.NightsStay,
                    isDay = false,
                    backgroundGradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
                )
            }
            3 -> WeatherCondition(
                description = "Overcast",
                icon = Icons.Rounded.Cloud,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF64748B))
            )
            45, 48 -> WeatherCondition(
                description = "Foggy",
                icon = Icons.Rounded.CloudQueue,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF374151), Color(0xFF4B5563), Color(0xFF6B7280))
            )
            51, 53, 55 -> WeatherCondition(
                description = "Drizzle",
                icon = Icons.Rounded.Grain,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF60A5FA))
            )
            61, 63, 65 -> WeatherCondition(
                description = if (weatherCode == 65) "Heavy Rain" else "Rain",
                icon = Icons.Rounded.WaterDrop,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF172554), Color(0xFF1E3A8A), Color(0xFF2563EB))
            )
            71, 73, 75, 77 -> WeatherCondition(
                description = if (weatherCode == 75) "Heavy Snow" else "Snow",
                icon = Icons.Rounded.AcUnit,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF94A3B8))
            )
            80, 81, 82 -> WeatherCondition(
                description = "Rain Showers",
                icon = Icons.Rounded.WaterDrop,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF3B82F6))
            )
            85, 86 -> WeatherCondition(
                description = "Snow Showers",
                icon = Icons.Rounded.AcUnit,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF64748B))
            )
            95, 96, 99 -> WeatherCondition(
                description = "Thunderstorm",
                icon = Icons.Rounded.Thunderstorm,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF4C1D95))
            )
            else -> WeatherCondition(
                description = "Partly Cloudy",
                icon = Icons.Rounded.WbCloudy,
                isDay = isDay,
                backgroundGradient = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6), Color(0xFF60A5FA))
            )
        }
    }
}

data class CurrentWeatherInfo(
    val cityName: String,
    val countryName: String,
    val temperature: Int,
    val apparentTemperature: Int,
    val humidity: Int,
    val windSpeed: Double,
    val precipitationChance: Int,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val tempMaxToday: Int,
    val tempMinToday: Int,
    val unit: TemperatureUnit,
    val lastUpdated: String
)

data class DailyForecastItem(
    val dateIso: String,
    val dayOfWeek: String,
    val dateFormatted: String,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val tempMax: Int,
    val tempMin: Int,
    val precipitationProbability: Int
)

data class WeatherReport(
    val city: City,
    val current: CurrentWeatherInfo,
    val fiveDayForecast: List<DailyForecastItem>
)

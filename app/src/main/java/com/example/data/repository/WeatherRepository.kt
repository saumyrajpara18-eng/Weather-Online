package com.example.data.repository

import com.example.data.api.WeatherApiService
import com.example.data.model.City
import com.example.data.model.CurrentWeatherInfo
import com.example.data.model.DailyForecastItem
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherCodeMapper
import com.example.data.model.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class WeatherRepository(
    private val apiService: WeatherApiService = WeatherApiService.create()
) {

    val popularCities: List<City> = listOf(
        City("London", "United Kingdom", "England", 51.5074, -0.1278),
        City("New York", "United States", "New York", 40.7128, -74.0060),
        City("Tokyo", "Japan", "Tokyo", 35.6762, 139.6503),
        City("Paris", "France", "Île-de-France", 48.8566, 2.3522),
        City("Sydney", "Australia", "New South Wales", -33.8688, 151.2093),
        City("San Francisco", "United States", "California", 37.7749, -122.4194)
    )

    suspend fun searchCities(query: String): Result<List<City>> = withContext(Dispatchers.IO) {
        try {
            if (query.trim().length < 2) {
                return@withContext Result.success(emptyList())
            }
            val response = apiService.searchCity(cityName = query.trim())
            val results = response.results.orEmpty().map { result ->
                City(
                    name = result.name,
                    country = result.country.orEmpty(),
                    admin1 = result.admin1,
                    latitude = result.latitude,
                    longitude = result.longitude
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeatherReport(
        city: City,
        unit: TemperatureUnit
    ): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getForecast(
                latitude = city.latitude,
                longitude = city.longitude,
                temperatureUnit = unit.apiValue,
                forecastDays = 6
            )

            val currentDto = response.current ?: throw IllegalStateException("No current weather data")
            val dailyDto = response.daily ?: throw IllegalStateException("No forecast data")

            val isDay = currentDto.isDay == 1
            val weatherCode = currentDto.weatherCode ?: 0
            val condition = WeatherCodeMapper.getCondition(weatherCode, isDay)

            val currentTemp = currentDto.temperature2m?.roundToInt() ?: 20
            val feelsLike = currentDto.apparentTemperature?.roundToInt() ?: currentTemp
            val humidity = currentDto.relativeHumidity2m ?: 50
            val windSpeed = currentDto.windSpeed10m ?: 0.0

            val todayMax = dailyDto.temperature2mMax?.firstOrNull()?.roundToInt() ?: currentTemp
            val todayMin = dailyDto.temperature2mMin?.firstOrNull()?.roundToInt() ?: currentTemp
            val precipChance = dailyDto.precipitationProbabilityMax?.firstOrNull() ?: 0

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val lastUpdated = timeFormatter.format(Date())

            val currentWeatherInfo = CurrentWeatherInfo(
                cityName = city.name,
                countryName = if (city.country.isNotBlank()) city.country else (city.admin1 ?: ""),
                temperature = currentTemp,
                apparentTemperature = feelsLike,
                humidity = humidity,
                windSpeed = windSpeed,
                precipitationChance = precipChance,
                weatherCode = weatherCode,
                condition = condition,
                isDay = isDay,
                tempMaxToday = todayMax,
                tempMinToday = todayMin,
                unit = unit,
                lastUpdated = lastUpdated
            )

            // Extract 5-day forecast
            val dailyTimes = dailyDto.time.orEmpty()
            val dailyCodes = dailyDto.weatherCode.orEmpty()
            val dailyMaxes = dailyDto.temperature2mMax.orEmpty()
            val dailyMins = dailyDto.temperature2mMin.orEmpty()
            val dailyPrecip = dailyDto.precipitationProbabilityMax.orEmpty()

            val fiveDayList = mutableListOf<DailyForecastItem>()
            val inputDateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayOfWeekFormatter = SimpleDateFormat("EEE", Locale.getDefault())
            val displayDateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

            // Index 0 is typically today, indices 1 to 5 are the 5 upcoming days.
            // If the list is short, start from index 0 or 1.
            val startIndex = if (dailyTimes.size > 5) 1 else 0
            val endIndex = (startIndex + 5).coerceAtMost(dailyTimes.size)

            val calendar = Calendar.getInstance()
            val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

            for (i in startIndex until endIndex) {
                val dateStr = dailyTimes.getOrNull(i) ?: continue
                val code = dailyCodes.getOrNull(i) ?: 1
                val maxTemp = dailyMaxes.getOrNull(i)?.roundToInt() ?: currentTemp
                val minTemp = dailyMins.getOrNull(i)?.roundToInt() ?: currentTemp
                val rainProb = dailyPrecip.getOrNull(i) ?: 0
                val dayCondition = WeatherCodeMapper.getCondition(code, isDay = true)

                var dayLabel = dateStr
                var formattedDate = dateStr
                try {
                    val parsedDate = inputDateParser.parse(dateStr)
                    if (parsedDate != null) {
                        val parsedCal = Calendar.getInstance().apply { time = parsedDate }
                        val diffDays = parsedCal.get(Calendar.DAY_OF_YEAR) - todayDayOfYear
                        dayLabel = when (diffDays) {
                            1 -> "Tomorrow"
                            0 -> "Today"
                            else -> dayOfWeekFormatter.format(parsedDate)
                        }
                        formattedDate = displayDateFormatter.format(parsedDate)
                    }
                } catch (_: Exception) {
                    // fallback to raw dateStr
                }

                fiveDayList.add(
                    DailyForecastItem(
                        dateIso = dateStr,
                        dayOfWeek = dayLabel,
                        dateFormatted = formattedDate,
                        weatherCode = code,
                        condition = dayCondition,
                        tempMax = maxTemp,
                        tempMin = minTemp,
                        precipitationProbability = rainProb
                    )
                )
            }

            val report = WeatherReport(
                city = city,
                current = currentWeatherInfo,
                fiveDayForecast = fiveDayList
            )
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

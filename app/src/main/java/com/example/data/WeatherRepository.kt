package com.example.data

import com.example.model.ConsensusWeatherReport
import com.example.model.DailyWeatherForecast
import com.example.model.HourlyWeatherPoint
import com.example.model.WeatherCityResult
import com.example.model.WeatherSourceReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Multi-source consensus weather service querying three independent providers in parallel:
 * 1. Open-Meteo (WMO model + hourly + 7-day forecast)
 * 2. MET Norway (Locationforecast 2.0 compact, Norwegian Meteorological Institute)
 * 3. wttr.in (JSON aggregated global feed)
 *
 * Averages temperature, wind speed, humidity across available sources and computes a consensus
 * weather condition + hourly/daily breakdown with full source transparency.
 */
object WeatherRepository {

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6500
    private const val USER_AGENT = "KeyDeckLauncher/1.0 (https://keydeck.app)"

    suspend fun searchCities(query: String): List<WeatherCityResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=ru&format=json"
        val jsonText = httpGetText(url) ?: return@withContext emptyList()
        parseGeocodingResults(jsonText)
    }

    suspend fun fetchConsensusWeather(city: WeatherCityResult): Result<ConsensusWeatherReport> =
        withContext(Dispatchers.IO) {
            val openMeteoDeferred = async { fetchOpenMeteo(city.latitude, city.longitude) }
            val metNoDeferred = async { fetchMetNorway(city.latitude, city.longitude) }
            val wttrDeferred = async { fetchWttr(city.latitude, city.longitude) }
            val yandexDeferred = async { fetchYandexWeather(city.latitude, city.longitude) }
            val googleDeferred = async { fetchGoogleWeather(city.name, city.latitude, city.longitude) }

            val openMeteo = openMeteoDeferred.await()
            val metNo = metNoDeferred.await()
            val wttr = wttrDeferred.await()
            val yandex = yandexDeferred.await()
            val google = googleDeferred.await()

            val report = buildConsensusReport(
                cityName = city.name,
                openMeteo = openMeteo,
                metNo = metNo,
                wttr = wttr,
                yandexWeather = yandex,
                googleWeather = google
            )
            if (report != null) {
                Result.success(report)
            } else {
                Result.failure(IllegalStateException("Не удалось получить данные ни от одного источника"))
            }
        }

    internal data class OpenMeteoParsed(
        val reading: WeatherSourceReading,
        val feelsLikeC: Double?,
        val iconKey: String,
        val hourly: List<HourlyWeatherPoint>,
        val daily: List<DailyWeatherForecast>
    )

    private fun fetchOpenMeteo(lat: Double, lon: Double): OpenMeteoParsed? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,wind_speed_10m_max" +
            "&forecast_days=7&timezone=auto"
        val body = httpGetText(url) ?: return null
        return parseOpenMeteoJson(body)
    }

    private fun fetchMetNorway(lat: Double, lon: Double): WeatherSourceReading? {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f"
            .format(Locale.US, lat, lon)
        val body = httpGetText(url) ?: return null
        return parseMetNorwayJson(body)
    }

    private fun fetchWttr(lat: Double, lon: Double): WeatherSourceReading? {
        val url = "https://wttr.in/%.4f,%.4f?format=j1&lang=ru".format(Locale.US, lat, lon)
        val body = httpGetText(url) ?: return null
        return parseWttrJson(body)
    }

    private fun fetchYandexWeather(lat: Double, lon: Double): WeatherSourceReading? {
        val url = "https://yandex.ru/pogoda/?lat=%.4f&lon=%.4f&lang=ru".format(Locale.US, lat, lon)
        val body = httpGetText(
            urlString = url,
            acceptHeader = "text/html,application/xhtml+xml,application/json",
            customUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        ) ?: return null
        return parseYandexWeatherPayload(body)
    }

    private fun fetchGoogleWeather(cityName: String, lat: Double, lon: Double): WeatherSourceReading? {
        val query = if (cityName.isNotBlank()) "weather+$cityName" else "%.4f,%.4f+weather".format(Locale.US, lat, lon)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.google.com/search?q=$encoded&hl=ru"
        val body = httpGetText(
            urlString = url,
            acceptHeader = "text/html,application/xhtml+xml",
            customUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        ) ?: return null
        return parseGoogleWeatherHtml(body)
    }

    internal fun parseGeocodingResults(rawJson: String): List<WeatherCityResult> {
        return runCatching {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    if (name.isEmpty()) continue
                    add(
                        WeatherCityResult(
                            name = name,
                            country = o.optString("country", ""),
                            admin = o.optString("admin1", ""),
                            latitude = o.optDouble("latitude", 0.0),
                            longitude = o.optDouble("longitude", 0.0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseOpenMeteoJson(rawJson: String): OpenMeteoParsed? {
        return runCatching {
            val root = JSONObject(rawJson)
            val current = root.optJSONObject("current") ?: return null
            val temp = current.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
            val feels = current.optDouble("apparent_temperature", Double.NaN).takeIf { !it.isNaN() }
            val wind = current.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }
            val humidity = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 }
            val wmoCode = current.optInt("weather_code", 0)
            val (condLabel, iconKey) = mapWmoCode(wmoCode)

            val hourlyObj = root.optJSONObject("hourly")
            val hourlyTimes = hourlyObj?.optJSONArray("time")
            val hourlyTemps = hourlyObj?.optJSONArray("temperature_2m")
            val hourlyPrecip = hourlyObj?.optJSONArray("precipitation_probability")
            val hourlyCodes = hourlyObj?.optJSONArray("weather_code")
            val hourlyList = mutableListOf<HourlyWeatherPoint>()
            if (hourlyTimes != null && hourlyTemps != null) {
                val limit = minOf(hourlyTimes.length(), hourlyTemps.length(), 24)
                for (i in 0 until limit step 3) {
                    val timeRaw = hourlyTimes.optString(i, "")
                    val hhmm = timeRaw.substringAfter('T', timeRaw).take(5)
                    val t = hourlyTemps.optDouble(i, 0.0).roundToInt()
                    val code = hourlyCodes?.optInt(i, wmoCode) ?: wmoCode
                    val prob = hourlyPrecip?.optInt(i, 0) ?: 0
                    hourlyList.add(
                        HourlyWeatherPoint(
                            timeLabel = hhmm,
                            tempC = t,
                            condition = mapWmoCode(code).first,
                            precipitationProb = prob
                        )
                    )
                }
            }

            val dailyObj = root.optJSONObject("daily")
            val dailyTimes = dailyObj?.optJSONArray("time")
            val dailyMin = dailyObj?.optJSONArray("temperature_2m_min")
            val dailyMax = dailyObj?.optJSONArray("temperature_2m_max")
            val dailyCodes = dailyObj?.optJSONArray("weather_code")
            val dailyWind = dailyObj?.optJSONArray("wind_speed_10m_max")
            val dailyList = mutableListOf<DailyWeatherForecast>()
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("EEE d", Locale.forLanguageTag("ru"))
            if (dailyTimes != null && dailyMin != null && dailyMax != null) {
                val limit = minOf(dailyTimes.length(), dailyMin.length(), dailyMax.length(), 7)
                for (i in 0 until limit) {
                    val dateRaw = dailyTimes.optString(i, "")
                    val dayLabel = runCatching {
                        inFmt.parse(dateRaw)?.let { outFmt.format(it).replaceFirstChar { c -> c.uppercaseChar() } }
                    }.getOrNull() ?: dateRaw
                    val minT = dailyMin.optDouble(i, 0.0).roundToInt()
                    val maxT = dailyMax.optDouble(i, 0.0).roundToInt()
                    val code = dailyCodes?.optInt(i, wmoCode) ?: wmoCode
                    val windMax = dailyWind?.optDouble(i, 0.0)?.roundToInt() ?: 0
                    dailyList.add(
                        DailyWeatherForecast(
                            dayLabel = dayLabel,
                            minTempC = minT,
                            maxTempC = maxT,
                            condition = mapWmoCode(code).first,
                            windMaxKmh = windMax
                        )
                    )
                }
            }

            OpenMeteoParsed(
                reading = WeatherSourceReading(
                    sourceName = "Open-Meteo",
                    tempC = temp,
                    condition = condLabel,
                    windKmh = wind,
                    humidityPercent = humidity
                ),
                feelsLikeC = feels,
                iconKey = iconKey,
                hourly = hourlyList,
                daily = dailyList
            )
        }.getOrNull()
    }

    internal fun parseMetNorwayJson(rawJson: String): WeatherSourceReading? {
        return runCatching {
            val root = JSONObject(rawJson)
            val props = root.optJSONObject("properties") ?: return null
            val series = props.optJSONArray("timeseries") ?: return null
            if (series.length() == 0) return null
            val first = series.optJSONObject(0) ?: return null
            val data = first.optJSONObject("data") ?: return null
            val instant = data.optJSONObject("instant")?.optJSONObject("details") ?: return null
            val temp = instant.optDouble("air_temperature", Double.NaN).takeIf { !it.isNaN() }
            val windMs = instant.optDouble("wind_speed", Double.NaN).takeIf { !it.isNaN() }
            val windKmh = windMs?.times(3.6)
            val humidity = instant.optDouble("relative_humidity", Double.NaN)
                .takeIf { !it.isNaN() }?.roundToInt()
            val symCode = data.optJSONObject("next_1_hours")
                ?.optJSONObject("summary")
                ?.optString("symbol_code")
                ?.takeIf { it.isNotBlank() }
                ?: data.optJSONObject("next_6_hours")
                    ?.optJSONObject("summary")
                    ?.optString("symbol_code")
                ?: "cloudy"

            WeatherSourceReading(
                sourceName = "MET Norway",
                tempC = temp,
                condition = mapMetSymbol(symCode).first,
                windKmh = windKmh,
                humidityPercent = humidity
            )
        }.getOrNull()
    }

    internal fun parseWttrJson(rawJson: String): WeatherSourceReading? {
        return runCatching {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("current_condition") ?: return null
            if (arr.length() == 0) return null
            val cur = arr.optJSONObject(0) ?: return null
            val temp = cur.optString("temp_C").toDoubleOrNull()
            val wind = cur.optString("windspeedKmph").toDoubleOrNull()
            val hum = cur.optString("humidity").toIntOrNull()
            val ruDesc = cur.optJSONArray("lang_ru")?.optJSONObject(0)?.optString("value").orEmpty()
            val enDesc = cur.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
            val desc = ruDesc.ifBlank { enDesc }.ifBlank { "Облачно" }

            WeatherSourceReading(
                sourceName = "wttr.in",
                tempC = temp,
                condition = desc,
                windKmh = wind,
                humidityPercent = hum
            )
        }.getOrNull()
    }

    internal fun parseYandexWeatherPayload(raw: String): WeatherSourceReading? {
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val fact = root.optJSONObject("fact") ?: return null
                val temp = fact.optDouble("temp", Double.NaN).takeIf { !it.isNaN() } ?: return null
                val windMs = fact.optDouble("wind_speed", Double.NaN).takeIf { !it.isNaN() }
                val hum = fact.optInt("humidity", -1).takeIf { it in 0..100 }
                val condRaw = fact.optString("condition", "cloudy")
                return WeatherSourceReading(
                    sourceName = "Яндекс Погода",
                    tempC = temp,
                    condition = mapYandexCondition(condRaw),
                    windKmh = windMs?.times(3.6),
                    humidityPercent = hum
                )
            }
            val factMatch = Regex(""""fact"\s*:\s*\{([^}]{10,900})\}""").find(raw)
            if (factMatch != null) {
                val block = factMatch.groupValues[1]
                val temp = Regex(""""temp"\s*:\s*(-?\d+(?:\.\d+)?)""").find(block)?.groupValues?.get(1)?.toDoubleOrNull()
                if (temp != null) {
                    val windMs = Regex(""""wind_speed"\s*:\s*(\d+(?:\.\d+)?)""").find(block)?.groupValues?.get(1)?.toDoubleOrNull()
                    val hum = Regex(""""humidity"\s*:\s*(\d{1,3})""").find(block)?.groupValues?.get(1)?.toIntOrNull()
                    val condRaw = Regex(""""condition"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1).orEmpty()
                    return WeatherSourceReading(
                        sourceName = "Яндекс Погода",
                        tempC = temp,
                        condition = mapYandexCondition(condRaw),
                        windKmh = windMs?.times(3.6),
                        humidityPercent = hum
                    )
                }
            }
            val htmlTemp = Regex("""temp__value[^>]*>([+\-−]?\d+)""").find(raw)?.groupValues?.get(1)
                ?.replace('−', '-')
                ?.toDoubleOrNull() ?: return null
            val htmlCond = Regex("""link__condition[^>]*>([^<]+)<""").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            WeatherSourceReading(
                sourceName = "Яндекс Погода",
                tempC = htmlTemp,
                condition = htmlCond.ifBlank { "Облачно" },
                windKmh = null,
                humidityPercent = null
            )
        }.getOrNull()
    }

    internal fun parseGoogleWeatherHtml(rawHtml: String): WeatherSourceReading? {
        return runCatching {
            val tempStr = Regex("""id="wob_tm"[^>]*>(-?\d+)<""").find(rawHtml)?.groupValues?.get(1)
                ?: Regex(""""temperature"\s*:\s*(-?\d+(?:\.\d+)?)""").find(rawHtml)?.groupValues?.get(1)
                ?: Regex("""class="[^"]*wob_t[^"]*"[^>]*>(-?\d+)<""").find(rawHtml)?.groupValues?.get(1)
                ?: return null
            val temp = tempStr.toDoubleOrNull() ?: return null
            val cond = Regex("""id="wob_dc"[^>]*>([^<]+)<""").find(rawHtml)?.groupValues?.get(1)?.trim()
                ?: Regex("""alt="([^"]+)"[^>]*id="wob_tci"""").find(rawHtml)?.groupValues?.get(1)?.trim()
                ?: "Облачно"
            val hum = Regex("""id="wob_hm"[^>]*>(\d+)%<""").find(rawHtml)?.groupValues?.get(1)?.toIntOrNull()
            val windRaw = Regex("""id="wob_ws"[^>]*>(\d+)""").find(rawHtml)?.groupValues?.get(1)?.toDoubleOrNull()
            val windUnit = Regex("""id="wob_ws"[^>]*>[^<]*(м/с|км/ч|mph)""", RegexOption.IGNORE_CASE)
                .find(rawHtml)?.groupValues?.get(1)?.lowercase().orEmpty()
            val windKmh = when {
                windRaw == null -> null
                windUnit.contains("м/с") -> windRaw * 3.6
                windUnit.contains("mph") -> windRaw * 1.609
                else -> windRaw
            }
            WeatherSourceReading(
                sourceName = "Google Погода",
                tempC = temp,
                condition = cond.ifBlank { "Облачно" },
                windKmh = windKmh,
                humidityPercent = hum
            )
        }.getOrNull()
    }

    private fun mapYandexCondition(raw: String): String = when (raw.lowercase()) {
        "clear" -> "Ясно"
        "partly-cloudy" -> "Малооблачно"
        "cloudy" -> "Облачно"
        "overcast" -> "Пасмурно"
        "drizzle", "light-rain" -> "Небольшой дождь"
        "rain", "moderate-rain" -> "Дождь"
        "heavy-rain", "continuous-heavy-rain", "showers" -> "Ливень"
        "wet-snow" -> "Дождь со снегом"
        "light-snow", "snow", "snow-showers" -> "Снег"
        "hail" -> "Град"
        "thunderstorm", "thunderstorm-with-rain", "thunderstorm-with-hail" -> "Гроза"
        else -> "Облачно"
    }

    internal fun buildConsensusReport(
        cityName: String,
        openMeteo: OpenMeteoParsed?,
        metNo: WeatherSourceReading?,
        wttr: WeatherSourceReading?,
        yandexWeather: WeatherSourceReading? = null,
        googleWeather: WeatherSourceReading? = null,
        nowMs: Long = System.currentTimeMillis()
    ): ConsensusWeatherReport? {
        val sources = listOfNotNull(openMeteo?.reading, yandexWeather, googleWeather, metNo, wttr)
        if (sources.isEmpty()) return null

        val temps = sources.mapNotNull { it.tempC }
        if (temps.isEmpty()) return null
        val avgTemp = temps.average()

        val winds = sources.mapNotNull { it.windKmh }
        val avgWind = if (winds.isNotEmpty()) winds.average().roundToInt() else 0

        val hums = sources.mapNotNull { it.humidityPercent }
        val avgHum = if (hums.isNotEmpty()) hums.average().roundToInt() else 0

        val feels = openMeteo?.feelsLikeC ?: avgTemp
        val primaryCond = openMeteo?.reading?.condition
            ?: yandexWeather?.condition
            ?: googleWeather?.condition
            ?: metNo?.condition
            ?: wttr?.condition
            ?: "Облачно"
        val iconKey = openMeteo?.iconKey ?: inferIconKeyFromText(primaryCond)

        return ConsensusWeatherReport(
            cityName = cityName,
            tempC = avgTemp.roundToInt(),
            feelsLikeC = feels.roundToInt(),
            condition = primaryCond,
            iconKey = iconKey,
            windKmh = avgWind,
            humidityPercent = avgHum,
            updatedAtMs = nowMs,
            sources = sources,
            hourly = openMeteo?.hourly.orEmpty(),
            daily = openMeteo?.daily.orEmpty()
        )
    }

    private fun mapWmoCode(code: Int): Pair<String, String> = when (code) {
        0 -> "Ясно" to "sun"
        1, 2 -> "Переменная облачность" to "partly"
        3 -> "Пасмурно" to "cloud"
        45, 48 -> "Туман" to "fog"
        51, 53, 55, 56, 57 -> "Морось" to "rain"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Дождь" to "rain"
        71, 73, 75, 77, 85, 86 -> "Снег" to "snow"
        95, 96, 99 -> "Гроза" to "storm"
        else -> "Облачно" to "cloud"
    }

    private fun mapMetSymbol(symbol: String): Pair<String, String> {
        val s = symbol.lowercase()
        return when {
            s.contains("thunder") -> "Гроза" to "storm"
            s.contains("snow") || s.contains("sleet") -> "Снег" to "snow"
            s.contains("rain") || s.contains("drizzle") -> "Дождь" to "rain"
            s.contains("fog") -> "Туман" to "fog"
            s.contains("clearsky") -> "Ясно" to "sun"
            s.contains("fair") || s.contains("partlycloudy") -> "Переменная облачность" to "partly"
            else -> "Облачно" to "cloud"
        }
    }

    private fun inferIconKeyFromText(cond: String): String {
        val c = cond.lowercase()
        return when {
            c.contains("гроз") || c.contains("thunder") -> "storm"
            c.contains("снег") || c.contains("snow") -> "snow"
            c.contains("дожд") || c.contains("лив") || c.contains("rain") -> "rain"
            c.contains("туман") || c.contains("fog") -> "fog"
            c.contains("ясн") || c.contains("солн") || c.contains("clear") -> "sun"
            c.contains("перемен") || c.contains("малооблач") -> "partly"
            else -> "cloud"
        }
    }

    private fun httpGetText(
        urlString: String,
        acceptHeader: String = "application/json",
        customUa: String = USER_AGENT
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", customUa)
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

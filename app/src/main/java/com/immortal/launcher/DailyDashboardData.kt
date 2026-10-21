/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

internal data class DashboardPlace(val title: String, val detail: String, val lat: Double, val lon: Double)
internal data class DashboardWeather(val temperature: Int, val feelsLike: Int, val code: Int, val high: Int, val low: Int, val rain: Int, val wind: Int, val observedAt: Long, val hours: List<DashboardHour> = emptyList())
internal data class DashboardHour(val at: Long, val temperature: Float)
internal data class DashboardKidEvent(val name: String, val activity: String, val time: String?)
internal data class DashboardTrain(val at: Long, val vehicle: String, val layover: Boolean)
internal data class DashboardTrains(val sourceAt: Long, val trains: List<DashboardTrain>)
internal const val DASHBOARD_WIDE_MIN_DP = 1500

/** A narrow dashboard owns vertical scrolling; horizontal face navigation remains available. */
internal fun dashboardOwnsDrag(scrollable: Boolean, editing: Boolean, dx: Float, dy: Float): Boolean =
    scrollable && !editing && kotlin.math.abs(dy) > kotlin.math.abs(dx)

internal fun dashboardDay(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
  timeZone = TimeZone.getTimeZone("America/Los_Angeles")
}.format(Date(at))

internal fun nextDashboardAlarm(hour: Int, minute: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
  require(hour in 0..23 && minute in 0..59)
  return Calendar.getInstance(zone).apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
  }.timeInMillis
}

internal object DailyDashboardData {
  val places = listOf(
      DashboardPlace("San Francisco", "94131", 37.745, -122.4383),
      DashboardPlace("Cupertino", "California", 37.323, -122.03218))
  const val TRAIN_FRESH_MS = 120_000L
  const val WEATHER_FRESH_MS = 90 * 60_000L
  const val STOP_PAGE = "https://www.sfmta.com/stops/church-st-28th-st-18157-new-stop"
  private const val FEED_BASE = "https://webservices.umoiq.com/api/pub/v1/agencies/sfmta-cis"

  fun weatherUrl(place: DashboardPlace): String =
      "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
          "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
          "&hourly=temperature_2m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
          "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=America%2FLos_Angeles" +
          "&timeformat=unixtime&forecast_days=1"

  fun parseWeather(json: String): DashboardWeather {
    val j = JSONObject(json)
    val current = j.getJSONObject("current")
    val daily = j.getJSONObject("daily")
    fun number(j: JSONObject, key: String): Int {
      val n = j.getDouble(key)
      require(n.isFinite())
      return n.roundToInt()
    }
    fun first(key: String): Int {
      val n = daily.getJSONArray(key).getDouble(0)
      require(n.isFinite())
      return n.roundToInt()
    }
    val stamp = current.getLong("time")
    require(stamp > 0)
    return DashboardWeather(number(current, "temperature_2m"), number(current, "apparent_temperature"),
        current.getInt("weather_code"), first("temperature_2m_max"), first("temperature_2m_min"),
        first("precipitation_probability_max").coerceIn(0, 100), number(current, "wind_speed_10m"), stamp * 1000, parseHours(j))
  }

  fun parseHours(json: JSONObject): List<DashboardHour> {
    val hourly = json.optJSONObject("hourly") ?: return emptyList()
    val times = hourly.getJSONArray("time")
    val temperatures = hourly.getJSONArray("temperature_2m")
    require(times.length() == temperatures.length())
    return (0 until times.length()).mapNotNull { i ->
      val at = times.optLong(i, 0) * 1000
      val temperature = temperatures.optDouble(i, Double.NaN)
      if (at <= 0 || !temperature.isFinite()) null else DashboardHour(at, temperature.toFloat())
    }.sortedBy { it.at }
  }

  fun parseEvents(json: String, day: String): List<DashboardKidEvent> {
    val entries = JSONObject(json).getJSONObject("days").optJSONArray(day) ?: return emptyList()
    return (0 until minOf(entries.length(), 20)).map { i ->
      val item = entries.getJSONObject(i)
      DashboardKidEvent(item.getString("name").take(40), item.getString("activity").take(60),
          item.optString("time").takeIf { it.isNotBlank() }?.take(30))
    }
  }

  /** Empty successful responses have no server stamp; receipt freshness is safe only without predictions. */
  fun trainFeedFresh(data: DashboardTrains, receivedAt: Long, now: Long): Boolean =
      fresh(if (data.sourceAt == 0L && data.trains.isEmpty()) receivedAt else data.sourceAt, now, TRAIN_FRESH_MS)

  /** Only through J trains from the inbound 28th St stop can reach Embarcadero. */
  fun parseTrains(json: String): DashboardTrains {
    val blocks = JSONArray(json)
    val times = mutableListOf<Long>()
    val trains = mutableListOf<DashboardTrain>()
    for (i in 0 until blocks.length()) {
      val block = blocks.getJSONObject(i)
      if (block.optJSONObject("route")?.optString("id") != "J" ||
          block.optJSONObject("stop")?.optString("code") != "18157") continue
      val stamp = block.getLong("serverTimestamp")
      require(stamp > 0)
      times += stamp
      val values = block.getJSONArray("values")
      for (n in 0 until values.length()) {
        val v = values.getJSONObject(n)
        val d = v.optJSONObject("direction") ?: continue
        if (!d.optString("id").startsWith("J_1_") ||
            !d.optString("destinationName").contains("Embarcadero", ignoreCase = true)) continue
        val at = v.optLong("timestamp", 0)
        if (at > 0) trains += DashboardTrain(at, v.optString("vehicleId"), v.optBoolean("affectedByLayover"))
      }
    }
    return DashboardTrains(times.minOrNull() ?: 0,
        trains.distinctBy { it.at to it.vehicle }.sortedBy { it.at })
  }

  fun fresh(stamp: Long, now: Long, maxAge: Long): Boolean = stamp > 0 && now - stamp in -30_000..maxAge

  fun upcoming(data: DashboardTrains, now: Long): List<DashboardTrain> =
      if (fresh(data.sourceAt, now, TRAIN_FRESH_MS)) data.trains.filter { it.at >= now }.take(3)
      else emptyList()

  /** Read SFMTA's public page settings rather than embedding its rotating website feed key. */
  fun predictionUrl(html: String): String {
    val raw = Regex("<script[^>]*data-drupal-selector=[\"']drupal-settings-json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(html)?.groupValues?.get(1)
        ?: error("Stop page settings unavailable")
    val settings = JSONObject(raw).getJSONObject("umo")
    require(settings.getString("base").trimEnd('/') == FEED_BASE)
    val key = settings.getString("key")
    require(key.isNotBlank())
    return FEED_BASE + "/stopcodes/18157/predictions?key=" + URLEncoder.encode(key, "UTF-8")
  }
}

/** Read-only public feeds. Failed requests never become fabricated zero-degree weather or arrivals. */
internal class DailyDashboardNetwork(private val cache: PortalOtaProviderCache? = null, private val get: suspend (String) -> String = ::dashboardHttpGet) {
  private var predictionEndpoint: String? = null

  suspend fun weather(place: DashboardPlace): DashboardWeather {
    val raw = get(DailyDashboardData.weatherUrl(place))
    val parsed = DailyDashboardData.parseWeather(raw)
    runCatching { cache?.put("weather-${DailyDashboardData.places.indexOf(place)}", raw) }
    return parsed
  }

  suspend fun trains(): DashboardTrains {
    try {
      val url = predictionEndpoint ?: DailyDashboardData.predictionUrl(get(DailyDashboardData.STOP_PAGE)).also { predictionEndpoint = it }
      val raw = get(url)
      val parsed = DailyDashboardData.parseTrains(raw)
      runCatching { cache?.put("transit", raw) }
      return parsed
    } catch (e: Exception) {
      predictionEndpoint = null
      throw e
    }
  }
}

private suspend fun dashboardHttpGet(url: String): String = suspendCancellableCoroutine { continuation ->
  val conn = URL(url).openConnection() as HttpURLConnection
  continuation.invokeOnCancellation { conn.disconnect() }
  try {
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    conn.setRequestProperty("User-Agent", "ImmortalGlass-PersonalDashboard/1.0")
    require(conn.responseCode == 200) { "Feed unavailable" }
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    if (continuation.isActive) continuation.resume(body)
  } catch (e: Exception) {
    if (continuation.isActive) continuation.resumeWithException(e)
  } finally {
    conn.disconnect()
  }
}

/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DailyDashboardDataTest {
  private val now = 1_792_509_202_000L
  private fun value(at: Long, destination: String = "Embarcadero Station", direction: String = "J_1_var1", vehicle: String = "2077") =
      """{"timestamp":$at,"vehicleId":"$vehicle","affectedByLayover":false,"direction":{"id":"$direction","destinationName":"$destination"}}"""
  private fun feed(values: String, route: String = "J", stop: String = "18157") =
      """[{"serverTimestamp":$now,"route":{"id":"$route"},"stop":{"code":"$stop"},"values":[$values]}]"""

  @Test fun weatherUsesSeparateFixedNeighborhoodCoordinatesAndFahrenheit() {
    val sf = DailyDashboardData.weatherUrl(DailyDashboardData.places[0])
    val cupertino = DailyDashboardData.weatherUrl(DailyDashboardData.places[1])
    assertTrue(sf.contains("latitude=37.745"))
    assertTrue(sf.contains("longitude=-122.4383"))
    assertTrue(cupertino.contains("latitude=37.323"))
    assertNotEquals(sf, cupertino)
    assertTrue(sf.contains("temperature_unit=fahrenheit"))
    assertTrue(sf.contains("timeformat=unixtime"))
  }

  @Test fun weatherReadsCurrentAndTodaysRangeWithoutReplacingMissingDataWithZero() {
    val json = """{"current":{"temperature_2m":63.6,"apparent_temperature":60.2,"weather_code":2,"wind_speed_10m":8.6,"time":1792509000},"daily":{"temperature_2m_max":[70.1],"temperature_2m_min":[53.8],"precipitation_probability_max":[15]}}"""
    val w = DailyDashboardData.parseWeather(json)
    assertEquals(64, w.temperature)
    assertEquals(60, w.feelsLike)
    assertEquals(70, w.high)
    assertEquals(54, w.low)
    assertEquals(15, w.rain)
    assertEquals(9, w.wind)
    assertEquals(1_792_509_000_000L, w.observedAt)
    assertThrows(Exception::class.java) { DailyDashboardData.parseWeather(json.replace("63.6", "null")) }
  }

  @Test fun trainFeedExcludesWrongDirectionOtherRoutesStopsAndShortTurns() {
    val correct = value(now + 180_000)
    val wrongWay = value(now + 60_000, "Balboa Park", "J_0_var0", "100")
    val shortTurn = value(now + 90_000, "Church & Duboce", "J_1_var0", "101")
    assertEquals(listOf(now + 180_000), DailyDashboardData.parseTrains(feed("$correct,$wrongWay,$shortTurn")).trains.map { it.at })
    assertTrue(DailyDashboardData.parseTrains(feed(correct, stop = "18158")).trains.isEmpty())
    assertTrue(DailyDashboardData.parseTrains(feed(correct, route = "N")).trains.isEmpty())
  }

  @Test fun trainFeedSortsAndDeduplicatesPredictions() {
    val a = value(now + 180_000)
    val b = value(now + 600_000, vehicle = "2090")
    val data = DailyDashboardData.parseTrains(feed("$b,$a,$a"))
    assertEquals(listOf(now + 180_000, now + 600_000), data.trains.map { it.at })
    assertEquals(now, data.sourceAt)
  }

  @Test fun countdownNeverShowsExpiredOrStaleArrivalsAsLive() {
    val data = DailyDashboardData.parseTrains(feed(value(now + 30_000) + "," + value(now + 300_000, vehicle = "2090")))
    assertEquals(2, DailyDashboardData.upcoming(data, now).size)
    assertEquals(1, DailyDashboardData.upcoming(data, now + 40_000).size)
    assertTrue(DailyDashboardData.upcoming(data, now + DailyDashboardData.TRAIN_FRESH_MS + 1).isEmpty())
    assertTrue(DailyDashboardData.upcoming(data, now - 60_000).isEmpty())
  }

  @Test fun emptyFeedIsDifferentFromMalformedOrErrorResponse() {
    assertTrue(DailyDashboardData.parseTrains("[]").trains.isEmpty())
    assertTrue(DailyDashboardData.parseTrains(feed("")).trains.isEmpty())
    assertThrows(Exception::class.java) { DailyDashboardData.parseTrains("{\"error\":\"unavailable\"}") }
    assertThrows(Exception::class.java) { DailyDashboardData.parseTrains(feed(value(now + 30_000)).replace(now.toString(), "0")) }
  }

  @Test fun networkUsesOfficialPageSettingsAndRefetchesThemAfterFailure() {
    val urls = mutableListOf<String>()
    val html = """<script type="application/json" data-drupal-selector="drupal-settings-json">{"umo":{"base":"https://webservices.umoiq.com/api/pub/v1/agencies/sfmta-cis","key":"public-page-test"}}</script>"""
    var fail = true
    val network = DailyDashboardNetwork { url ->
      urls += url
      if (url == DailyDashboardData.STOP_PAGE) html else {
        if (fail) { fail = false; error("temporary failure") }
        feed(value(now + 180_000))
      }
    }
    assertThrows(Exception::class.java) { runBlocking { network.trains() } }
    assertEquals(1, runBlocking { network.trains() }.trains.size)
    assertEquals(2, urls.count { it == DailyDashboardData.STOP_PAGE })
    assertTrue(urls.last().contains("/stopcodes/18157/predictions?key=public-page-test"))
    assertThrows(Exception::class.java) { DailyDashboardData.predictionUrl(html.replace("webservices.umoiq.com", "example.com")) }
  }

  @Test fun hourlyForecastPreservesTimesAndSkipsMissingSamples() {
    val hours = DailyDashboardData.parseHours(org.json.JSONObject("""{"hourly":{"time":[100,3700,7300],"temperature_2m":[55.5,null,58.2]}}"""))
    assertEquals(listOf(100_000L, 7_300_000L), hours.map { it.at })
    assertEquals(listOf(55.5f, 58.2f), hours.map { it.temperature })
    assertTrue(DailyDashboardData.parseHours(org.json.JSONObject("{}")).isEmpty())
    assertTrue(DailyDashboardData.weatherUrl(DailyDashboardData.places.first()).contains("hourly=temperature_2m"))
  }

  @Test fun successfulEmptyTrainResponseUsesReceiptFreshnessButNeverRevivesStalePredictions() {
    val empty = DailyDashboardData.parseTrains("[]")
    assertTrue(DailyDashboardData.trainFeedFresh(empty, now, now + 30_000))
    assertFalse(DailyDashboardData.trainFeedFresh(empty, now, now + 120_001))
    val stale = DashboardTrains(now - 180_000, listOf(DashboardTrain(now + 300_000, "1", false)))
    assertFalse(DailyDashboardData.trainFeedFresh(stale, now, now))
    assertTrue(DailyDashboardData.upcoming(stale, now).isEmpty())
  }

  @Test fun kidsActivitiesAreOnlyShownOnTheirDatedLocalDay() {
    val json = """{"days":{"2026-10-20":[{"name":"Child","activity":"Soccer"}]}}"""
    assertEquals("Soccer", DailyDashboardData.parseEvents(json, "2026-10-20").single().activity)
    assertTrue(DailyDashboardData.parseEvents(json, "2026-10-21").isEmpty())
    assertEquals("2026-10-20", dashboardDay(1_792_516_800_000L))
  }

  @Test fun alarmTimeRollsToTomorrowWhenTodaysTimeHasPassed() {
    val zone = java.util.TimeZone.getTimeZone("America/Los_Angeles")
    val from = java.util.Calendar.getInstance(zone).apply { clear(); set(2026, 9, 20, 16, 30, 20) }.timeInMillis
    val sameDay = nextDashboardAlarm(17, 0, from, zone)
    val nextDay = nextDashboardAlarm(16, 30, from, zone)
    assertEquals(29 * 60_000L + 40_000L, sameDay - from)
    assertEquals(24 * 60 * 60_000L - 20_000L, nextDay - from)
    assertEquals(0L, sameDay % 60_000L)
  }

  @Test fun narrowDashboardScrollsWithoutTakingHorizontalFaceNavigationOrEditGestures() {
    assertTrue(dashboardOwnsDrag(scrollable = true, editing = false, dx = 4f, dy = -80f))
    assertTrue(dashboardOwnsDrag(scrollable = true, editing = false, dx = 4f, dy = 80f))
    assertFalse(dashboardOwnsDrag(scrollable = true, editing = false, dx = -80f, dy = 4f))
    assertFalse(dashboardOwnsDrag(scrollable = false, editing = false, dx = 4f, dy = -80f))
    assertFalse(dashboardOwnsDrag(scrollable = true, editing = true, dx = 4f, dy = -80f))
  }
}

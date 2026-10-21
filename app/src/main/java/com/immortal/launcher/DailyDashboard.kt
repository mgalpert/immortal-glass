/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun portalColor(key: String, fallback: Long): Color = PortalOta.current?.theme?.get(key)?.let {
  runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
} ?: Color(fallback)
internal val DashboardInk get() = portalColor("dashboardInk", 0xFFF8F5FF)
internal val DashboardDim = Color(0xFFDED9EB)
internal val DashboardGold = Color(0xFFFFD79C)
internal val DashboardMint = Color(0xFFA8F2E2)

/** Original family encouragements; no borrowed quotations or author attributions. */
private val dashboardQuotes = listOf(
    "Keep going. One small step still moves you forward.",
    "Hard things become possible when you keep practicing.",
    "Be kind, even when your day is hard.",
    "Listen with care. Everyone deserves respect.",
    "You can take a break and still keep going.",
    "You don’t have to get it right to learn from trying.",
    "Small acts of kindness can change someone’s day.",
    "Speak to others the way you hope they’ll speak to you.",
    "Progress grows from patience, practice, and another try.",
    "Ask for help. Brave people don’t do every hard thing alone.",
    "Be proud of your effort, and generous with your kindness.",
    "You can disagree and still treat someone with respect.",
    "Mistakes are part of learning. Take a breath and try again.",
    "Do the next hard thing, one small step at a time.",
    "Notice who needs a friend. Make room for them.",
    "Kind words and careful listening are signs of strength.")

/** Eight widgets in the family layout. Work stops while another face or app is showing. */
@Composable
internal fun DailyDashboard(active: Boolean, use24Hour: Boolean, backdrop: GlassBackdrop?) {
  val polling = dashboardIsVisible(active)
  val release = PortalOta.current
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(polling) {
    if (polling) while (true) {
      now = System.currentTimeMillis()
      delay(10_000 - now % 10_000)
    }
  }
  BoxWithConstraints(Modifier.fillMaxSize().background(portalColor("dashboardBackground", 0xFF101216))) {
    val dashboardWidth = maxWidth
    backdrop?.let { Image(it.sharp, null, Modifier.fillMaxSize()) }
    Box(Modifier.fillMaxSize().background(Color(0x14000000)))
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
      val wide = dashboardWidth >= DASHBOARD_WIDE_MIN_DP.dp
      val gap = 20.dp
      @Composable fun Tile(slot: Int, modifier: Modifier) {
        val index = release?.order?.get(slot) ?: slot
        key(index) { DashboardTile(backdrop, origin, modifier, padding = if (index == 4 || index == 6) 16.dp else 24.dp) {
          when (index) {
            0, 1 -> DashboardWeatherCard(DailyDashboardData.places[index], polling, now)
            2 -> DashboardTrainCard(polling, now)
            3 -> DashboardClockCard(polling, now, use24Hour)
            4 -> DashboardPhotoCard(polling)
            5 -> DashboardKidsCard(polling, now)
            6 -> DashboardSonosCard(polling)
            7 -> DashboardMuseCard(polling)
          }
        } }
      }
      @Composable fun WeatherColumn(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
          Tile(0, Modifier.weight(1f).fillMaxWidth())
          Tile(1, Modifier.weight(1f).fillMaxWidth())
        }
      }
      @Composable fun FamilyColumn(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
          Tile(3, Modifier.weight(1f).fillMaxWidth())
          Tile(5, Modifier.weight(3f).fillMaxWidth())
        }
      }
      @Composable fun UtilityColumn(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
          Tile(2, Modifier.weight(2f).fillMaxWidth())
          Tile(6, Modifier.weight(1f).fillMaxWidth())
          Tile(7, Modifier.weight(1f).fillMaxWidth())
        }
      }
      val day = PortalOtaProtocol.localDay(now)
      val remoteDay = release?.day(now)
      val quotes = if (remoteDay != null) release.quotes else dashboardQuotes.mapIndexed { i, q -> "bundled-$i" to q }.toMap()
      val ids = quotes.keys.toList()
      val defaultId = remoteDay?.quoteId ?: ids[Math.floorMod(PortalOtaProtocol.date(day) / 86_400_000L, ids.size.toLong()).toInt()]
      var tappedId by remember(day) { mutableStateOf<String?>(null) }
      val selectedId = tappedId?.takeIf { it in quotes } ?: defaultId
      Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 92.dp)) {
        Box(Modifier.fillMaxWidth().height(74.dp).clickable(onClickLabel = "Next quote", role = androidx.compose.ui.semantics.Role.Button) {
          tappedId = ids[(ids.indexOf(selectedId) + 1) % ids.size]
        }, contentAlignment = Alignment.Center) {
          Text("“${quotes[selectedId]}”", style = text(if (wide) 32 else 26, FontWeight.Medium, DashboardInk, androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
        }
        Text(if (release == null) "Daily content · bundled" else "Daily content · r${release.revision}",
            Modifier.align(Alignment.End), style = text(12, FontWeight.Normal, DashboardDim))
        Spacer(Modifier.height(8.dp))
        if (wide) {
          Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            WeatherColumn(Modifier.weight(1f).fillMaxHeight())
            FamilyColumn(Modifier.weight(1f).fillMaxHeight())
            Tile(4, Modifier.weight(1.1f).fillMaxHeight())
            UtilityColumn(Modifier.weight(0.9f).fillMaxHeight())
          }
        } else {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().height(850.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
              WeatherColumn(Modifier.weight(1f).fillMaxHeight())
              FamilyColumn(Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(850.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
              Tile(4, Modifier.weight(1f).fillMaxHeight())
              UtilityColumn(Modifier.weight(1f).fillMaxHeight())
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun dashboardIsVisible(active: Boolean): Boolean {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  return active && foreground
}

@Composable
internal fun DashboardTile(backdrop: GlassBackdrop?, origin: Offset, modifier: Modifier, padding: Dp = 24.dp, content: @Composable BoxScope.() -> Unit) {
  var tile by remember { mutableStateOf(Rect.Zero) }
  val press = rememberPress()
  val radius = with(LocalDensity.current) { 30.dp.toPx() }
  val glass = backdrop?.let { Modifier.glass(it, tile, press, false, radius) } ?: Modifier.frosted(radius = radius)
  Box(modifier.onGloballyPositioned { tile = Rect(it.positionInRoot() - origin, it.size.toSize()) }
      .then(glass).padding(padding), content = content)
}

@Composable
internal fun rememberDashboardBackdrop(width: Int, height: Int): GlassBackdrop? {
  val theme = PortalOta.current?.theme?.get("dashboardBackground")
  val backdrop by produceState<GlassBackdrop?>(null, width, height, theme) {
    if (width > 8 && height > 8) value = withContext(Dispatchers.Default) {
      buildGlassBackdrop(dashboardBackground(), width, height, emptyList())
    }
  }
  return backdrop
}

internal fun dashboardClock(at: Long, use24Hour: Boolean, seconds: Boolean = false): String =
    SimpleDateFormat(if (use24Hour) { if (seconds) "HH:mm:ss" else "HH:mm" } else { if (seconds) "h:mm:ss a" else "h:mm a" }, Locale.US).format(Date(at))

@Composable
private fun DashboardWeatherCard(place: DashboardPlace, active: Boolean, now: Long) {
  val context = LocalContext.current
  val cache = remember { PortalOtaProviderCache(java.io.File(context.filesDir, "portal-provider-cache")) }
  val network = remember { DailyDashboardNetwork(cache = cache) }
  val provider = PortalOta.current?.providers?.get("weather") ?: (900 to 5400)
  var value by remember(place) { mutableStateOf<DashboardWeather?>(null) }
  var failed by remember { mutableStateOf(false) }
  var refresh by remember { mutableIntStateOf(0) }
  LaunchedEffect(active, refresh, provider) {
    if (value == null) value = withContext(Dispatchers.IO) { cache.get("weather-${DailyDashboardData.places.indexOf(place)}")?.let { runCatching { DailyDashboardData.parseWeather(it.first) }.getOrNull() } }
    if (active) while (true) {
      val result = withContext(Dispatchers.IO) { runCatching { network.weather(place) } }
      result.onSuccess { value = it; failed = false }.onFailure { failed = true }
      delay(if (failed) 60_000 else provider.first * 1000L)
    }
  }
  val reading = value
  val fresh = reading != null && DailyDashboardData.fresh(reading.observedAt, now, provider.second * 1000L)
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(place.title, style = text(29, color = DashboardInk))
    Text(place.detail, style = text(18, FontWeight.Normal, DashboardDim))
    if (reading != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${reading.temperature}°", Modifier.weight(1f), style = text(68, FontWeight.Light, DashboardInk))
        Text(Weather.emoji(reading.code), style = text(48))
      }
      Text("${weatherLabel(reading.code)} · °F", style = text(20, FontWeight.Normal, DashboardInk))
      Text("High ${reading.high}°   Low ${reading.low}°", style = text(23, FontWeight.Medium, DashboardInk))
      val today = dashboardDay(now)
      val hours = remember(reading.hours, today) { reading.hours.filter { dashboardDay(it.at) == today } }
      Spacer(Modifier.height(6.dp))
      Text("TODAY · HOURLY FORECAST", style = text(13, FontWeight.Medium, DashboardDim))
      TemperatureChart(hours, now, Modifier.fillMaxWidth().weight(1f))
    } else {
      Text("—°", style = text(68, FontWeight.Light, DashboardInk))
      Text(if (failed) "Weather unavailable" else "Getting the forecast…", style = text(20, color = DashboardDim))
      Spacer(Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(when {
        reading == null -> "Open-Meteo"
        !fresh -> "Last known weather"
        failed -> "Refresh failed · last update"
        else -> "Open-Meteo · ${dashboardClock(reading.observedAt, false)}"
      }, Modifier.weight(1f), style = text(13, FontWeight.Normal, if (failed || (reading != null && !fresh)) DashboardGold else DashboardDim))
      TextButton(onClick = { refresh++ }, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text("↻", style = text(24, color = DashboardInk))
      }
    }
  }
}

@Composable
private fun TemperatureChart(hours: List<DashboardHour>, now: Long, modifier: Modifier) {
  if (hours.size < 2) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("Hourly forecast unavailable", style = text(15, FontWeight.Normal, DashboardDim)) }
    return
  }
  Column(modifier) {
    Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
      val min = hours.minOf { it.temperature } - 2f
      val range = (hours.maxOf { it.temperature } + 2f - min).coerceAtLeast(4f)
      val first = hours.first().at
      val span = (hours.last().at - first).coerceAtLeast(1L)
      fun point(hour: DashboardHour) = Offset((hour.at - first).toFloat() / span * size.width,
          size.height - (hour.temperature - min) / range * size.height)
      repeat(3) { i ->
        val y = size.height * i / 2f
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 1f)
      }
      val line = Path()
      hours.forEachIndexed { i, h ->
        val p = point(h)
        if (i == 0 || h.at - hours[i - 1].at > 90 * 60_000L) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
      }
      drawPath(line, DashboardMint, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
      if (now in first..hours.last().at) {
        val x = (now - first).toFloat() / span * size.width
        drawLine(Color.White.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), 1f)
        val next = hours.indexOfFirst { it.at >= now }.coerceAtLeast(1)
        val a = hours[next - 1]; val b = hours[next]
        if (b.at - a.at <= 90 * 60_000L) {
          val t = (now - a.at).toFloat() / (b.at - a.at).coerceAtLeast(1L)
          val y = point(a).y + (point(b).y - point(a).y) * t
          drawCircle(DashboardMint, 5.dp.toPx(), Offset(x, y))
          drawCircle(Color.White, 2.dp.toPx(), Offset(x, y))
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      listOf(0, hours.size / 4, hours.size / 2, hours.size * 3 / 4, hours.lastIndex).distinct().forEach { i ->
        Text(SimpleDateFormat("ha", Locale.US).format(Date(hours[i].at)).lowercase(), style = text(12, FontWeight.Normal, DashboardDim))
      }
    }
  }
}

@Composable
private fun DashboardTrainCard(active: Boolean, now: Long) {
  val context = LocalContext.current
  val cache = remember { PortalOtaProviderCache(java.io.File(context.filesDir, "portal-provider-cache")) }
  val network = remember { DailyDashboardNetwork(cache = cache) }
  val provider = PortalOta.current?.providers?.get("transit") ?: (30 to 120)
  var value by remember { mutableStateOf<DashboardTrains?>(null) }
  var receivedAt by remember { mutableLongStateOf(0L) }
  var failed by remember { mutableStateOf(false) }
  var refresh by remember { mutableIntStateOf(0) }
  LaunchedEffect(active, refresh, provider) {
    if (value == null) withContext(Dispatchers.IO) { cache.get("transit") }?.let { cached ->
      value = runCatching { DailyDashboardData.parseTrains(cached.first) }.getOrNull(); receivedAt = cached.second
    }
    if (active) while (true) {
      val result = withContext(Dispatchers.IO) { runCatching { network.trains() } }
      result.onSuccess { value = it; receivedAt = System.currentTimeMillis(); failed = false }.onFailure { failed = true }
      delay(provider.first * 1000L)
    }
  }
  val data = value
  val fresh = data != null && DailyDashboardData.trainFeedFresh(data, receivedAt, now) && DailyDashboardData.fresh(receivedAt, now, provider.second * 1000L)
  val trains = if (fresh) data?.let { DailyDashboardData.upcoming(it, now) }.orEmpty() else emptyList()
  val first = trains.firstOrNull()
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("J train", Modifier.weight(1f), style = text(31, color = DashboardGold))
      TextButton(onClick = { refresh++ }, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("↻", style = text(24, color = DashboardInk))
      }
    }
    Spacer(Modifier.weight(1f))
    if (first != null) {
      val minutes = (first.at - now) / 60_000
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (minutes == 0L) "Due" else (if (first.layover) "≈" else "") + minutes.toString(), style = text(112, FontWeight.Light, DashboardInk))
        if (minutes > 0) Text("min", Modifier.padding(bottom = 17.dp), style = text(34, FontWeight.Normal, DashboardDim))
      }
      if (trains.size > 1) {
        Text(trains.drop(1).joinToString("  ·  ") { "${(it.at - now) / 60_000} min" }, style = text(36, FontWeight.Medium, DashboardInk))
      }
      if (first.layover) Text("≈ Terminal estimate", style = text(15, FontWeight.Normal, DashboardDim))
    } else {
      Text("—", style = text(112, FontWeight.Light, DashboardInk))
      Text(when {
        data == null && !failed -> "Checking arrivals…"
        !fresh || failed -> "Live arrivals unavailable"
        else -> "No predictions"
      }, style = text(22, FontWeight.Normal, DashboardDim))
    }
    Spacer(Modifier.weight(1f))
    if (failed && fresh) Text("Refresh failed · previous arrivals", style = text(14, FontWeight.Normal, DashboardGold))
  }
}

/** A quiet neutral backdrop keeps the glass highlights and foreground content distinct. */
private fun dashboardBackground() = BackgroundSource { width, height ->
  val bitmap = createBitmap(width, height)
  val canvas = android.graphics.Canvas(bitmap)
  val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG)
  val bg = PortalOta.current?.theme?.get("dashboardBackground")?.let { android.graphics.Color.parseColor(it) } ?: 0xFF101216.toInt()
  paint.shader = android.graphics.RadialGradient(width * 0.45f, height * 0.05f, maxOf(width, height) * 0.9f,
      intArrayOf(androidx.core.graphics.ColorUtils.blendARGB(bg, android.graphics.Color.WHITE, .13f), bg, androidx.core.graphics.ColorUtils.blendARGB(bg, android.graphics.Color.BLACK, .2f)),
      floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
  canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
  bitmap
}

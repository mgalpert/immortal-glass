/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun DashboardClockCard(active: Boolean, now: Long, use24Hour: Boolean) {
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
    Text(SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date(now)), style = text(18, FontWeight.Normal, DashboardDim))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.US).format(Date(now)), style = text(60, FontWeight.Light, DashboardInk))
      if (!use24Hour) Text(SimpleDateFormat("a", Locale.US).format(Date(now)), Modifier.padding(bottom = 10.dp), style = text(20, FontWeight.Normal, DashboardDim))
    }
    DashboardAlarmControls(active, now, use24Hour)
  }
}

/** Daily content is a dated local file, never a repeating hard-coded family schedule. */
@Composable
internal fun DashboardKidsCard(active: Boolean, now: Long) {
  val context = LocalContext.current
  val day = dashboardDay(now)
  var events by remember { mutableStateOf<List<DashboardKidEvent>>(emptyList()) }
  var failed by remember { mutableStateOf(false) }
  val release = PortalOta.current
  LaunchedEffect(active, day, release?.revision) {
    if (active) while (true) {
      val result = withContext(Dispatchers.IO) { runCatching {
        val file = File(context.getExternalFilesDir(null), "dashboard-events.json")
        if (release != null) release.events[day].orEmpty()
        else if (!file.exists()) emptyList() else {
          require(file.length() <= 256_000)
          DailyDashboardData.parseEvents(file.readText(), day)
        }
      } }
      result.onSuccess { events = it; failed = false }.onFailure { events = emptyList(); failed = true }
      delay(60_000)
    }
  }
  val colors = remember { listOf(DashboardMint, Color(0xFFA3C8FF), DashboardGold, Color(0xFFF1AAA7)) }
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("After school", style = text(32, color = DashboardInk))
    if (events.isEmpty()) {
      Spacer(Modifier.weight(1f))
      Text(if (failed) "Activities unavailable" else "No activities added for today", style = text(24, FontWeight.Normal, DashboardDim))
      Spacer(Modifier.weight(1f))
    } else {
      events.take(4).forEachIndexed { index, event ->
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(56.dp).background(colors[index], CircleShape), contentAlignment = Alignment.Center) {
            val categoryIcon = when (event.activity.trim().lowercase(Locale.ROOT)) {
              "soccer", "football" -> "⚽"
              "dance", "ballet" -> "🎵"
              "playdate", "play date" -> "🎈"
              else -> event.name.take(1)
            }
            Text(categoryIcon, style = text(28, color = Color(0xFF171B22)))
          }
          Column(Modifier.weight(1f)) {
            Text(event.name, style = text(32, color = DashboardInk), maxLines = 1)
            Text(event.activity, style = text(24, FontWeight.Normal, DashboardDim))
          }
          event.time?.let { Text(it, style = text(20, FontWeight.Medium, DashboardMint)) }
        }
      }
      Text(if (events.size > 4) "+${events.size - 4} more activities" else if (events.all { it.time == null }) "Today · times not added" else "Today’s activities", style = text(14, FontWeight.Normal, DashboardDim))
    }
  }
}

@Composable
private fun DashboardAlarmControls(active: Boolean, now: Long, use24Hour: Boolean) {
  val context = LocalContext.current
  var state by remember { mutableStateOf(TimerStore.load(context)) }
  DisposableEffect(context) {
    val listener = { state = TimerStore.load(context) }
    TimerStore.addListener(listener)
    onDispose { TimerStore.removeListener(listener) }
  }
  LaunchedEffect(active) { if (active) state = TimerStore.load(context) }
  fun pickAlarm() {
    val current = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis(); add(Calendar.HOUR_OF_DAY, 1) }
    TimePickerDialog(context, { _, hour, minute ->
      fun setAlarm() {
        val at = System.currentTimeMillis()
        TimerAlarm.stop(context)
        TimerStore.start(context, nextDashboardAlarm(hour, minute, at) - at, at)
      }
      val existing = TimerStore.load(context)
      if (existing.active(System.currentTimeMillis()) || existing.ringing) {
        AlertDialog.Builder(context).setTitle("Replace the current alarm / timer?")
            .setMessage("This Portal has one shared alarm and countdown timer.")
            .setNegativeButton("Keep current", null).setPositiveButton("Set alarm") { _, _ -> setAlarm() }.show()
      } else setAlarm()
    }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), use24Hour).apply {
      setTitle("Set alarm")
      show()
    }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = ::pickAlarm, contentPadding = PaddingValues(horizontal = 4.dp)) {
      val label = when {
        state.running -> "Alarm " + (if (dashboardDay(state.endsAt) == dashboardDay(now)) "" else "tomorrow ") + dashboardClock(state.endsAt, use24Hour)
        state.ringing -> "Alarm ringing"
        state.remainingMs > 0 -> "Timer paused"
        else -> "Set alarm"
      }
      Text(label, style = text(18, color = DashboardMint), maxLines = 1)
    }
    Spacer(Modifier.weight(1f))
    if (state.running || state.ringing || state.remainingMs > 0) TextButton(onClick = {
      TimerAlarm.stop(context)
      TimerStore.clear(context)
    }, contentPadding = PaddingValues(horizontal = 4.dp)) {
      Text(if (state.ringing) "Stop" else "Cancel", style = text(17, color = DashboardDim))
    }
  }
}

private data class DashboardSprite(val image: ImageBitmap, val cell: Int, val columns: Int, val frames: Int, val fps: Double)

/** Read Muse's installed avatar rather than bundling another copy of its character. */
@Composable
internal fun DashboardMuseCard(active: Boolean) {
  val context = LocalContext.current
  var sprite by remember { mutableStateOf<DashboardSprite?>(null) }
  var installed by remember { mutableStateOf(false) }
  LaunchedEffect(active) {
    if (active) {
      installed = museInstalled(context)
      sprite = withContext(Dispatchers.IO) { runCatching {
        val assets = context.createPackageContext(MUSE, 0).assets
        val manifest = JSONObject(assets.open("muse-avatar/manifest.json").bufferedReader().use { it.readText() })
        val idle = manifest.getJSONObject("animations").getJSONObject("idle")
        val cell = manifest.getInt("cell"); val frames = idle.getInt("frames"); val columns = idle.getInt("columns")
        val fps = manifest.getDouble("fps")
        require(cell in 1..256 && frames in 1..500 && columns in 1..50 && fps in 1.0..30.0)
        val bmp = assets.open("muse-avatar/idle.png").use { BitmapFactory.decodeStream(it) } ?: error("No avatar")
        require(bmp.width >= columns * cell && bmp.height >= ((frames + columns - 1) / columns) * cell)
        DashboardSprite(bmp.asImageBitmap(), cell, columns, frames, fps)
      }.getOrNull() }
    }
  }
  Row(Modifier.fillMaxSize().clickable(enabled = installed) { heyMuse(context) }
      .semantics { contentDescription = "Muse. Tap to talk to Muse." },
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Box(Modifier.width(120.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
      val avatar = sprite
      if (avatar != null) DashboardJolly(avatar, active)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(if (installed) "Hey Muse" else "Muse unavailable", style = text(27, color = DashboardInk))
      Text("Tap to talk", style = text(17, FontWeight.Normal, DashboardDim))
    }
  }
}

@Composable
private fun DashboardJolly(sprite: DashboardSprite, active: Boolean) {
  var frame by remember(sprite) { mutableIntStateOf(0) }
  LaunchedEffect(active, sprite) {
    if (active) while (true) {
      delay((1000 / sprite.fps).toLong())
      frame = (frame + 1) % sprite.frames
    }
  }
  Canvas(Modifier.size(120.dp)) {
    drawImage(sprite.image,
        srcOffset = IntOffset(frame % sprite.columns * sprite.cell, frame / sprite.columns * sprite.cell),
        srcSize = IntSize(sprite.cell, sprite.cell), dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
  }
}

@Composable
internal fun DashboardPhotoCard(active: Boolean) {
  val context = LocalContext.current
  var photo by remember { mutableStateOf<Bitmap?>(null) }
  var position by remember { mutableIntStateOf(0) }
  var retainedPath by remember { mutableStateOf(PortalPhotoCursor.path) }
  val release = PortalOta.current
  val photoSeconds = release?.providers?.get("photos")?.first ?: 30
  var seenRevision by remember { mutableLongStateOf(release?.revision ?: 0) }
  var count by remember { mutableIntStateOf(0) }
  var next by remember { mutableIntStateOf(0) }
  LaunchedEffect(active, next, photoSeconds, release?.revision) {
    if (seenRevision != (release?.revision ?: 0L)) { retainedPath = PortalPhotoCursor.path; seenRevision = release?.revision ?: 0L }
    if (active) while (true) {
      val result = withContext(Dispatchers.IO) { runCatching {
        val settings = ScreensaverConfig.load(context)
        val folder = if (settings.usesFolder) File(settings.folderPath!!) else context.getExternalFilesDir("dashboard-album")
        val local = folder?.let { LocalMedia.enumerate(it.path, includeVideo = false, max = 200) }
            .orEmpty().map { it.path to it.path }
        val remote = release?.assets.orEmpty().filter { it.id.startsWith("photo-") }.mapNotNull { a ->
          release?.asset(a.id)?.let { file -> "asset:${a.id}" to file.path }
        }
        val candidates = (local + remote).filter { isPortraitDashboardPhoto(it.second) }
        val n = candidates.size
        val retained = candidates.indexOfFirst { it.first == retainedPath }
        val index = if (n == 0) 0 else if (retained >= 0) retained else position % n
        if (n > 0) PortalPhotoCursor.path = candidates[index].first
        Triple(n, index, if (n == 0) null else decodeDashboardPhoto(candidates[index].second))
      }.getOrNull() }
      retainedPath = null
      count = result?.first ?: 0
      photo = result?.third
      if (count > 0) position = (result!!.second + 1) % count
      delay(if (count > 0 && photo == null) 5_000 else photoSeconds * 1000L)
    }
  }
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("PHOTOS", Modifier.weight(1f), style = text(15, color = DashboardDim))
      if (count > 0) Text("$count photos · ${photoSeconds}s", style = text(14, FontWeight.Normal, DashboardDim))
    }
    val image = photo
    if (image != null) Image(image.asImageBitmap(), "Photo album", Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).clickable { next++ }, contentScale = ContentScale.Fit)
    else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      Text(if (count > 0) "Skipping an unreadable photo…" else "Add portrait photos to your album", style = text(22, FontWeight.Normal, DashboardDim))
    }
    Text(if (image != null) "Your moments · tap for next" else "Your photos stay on this Portal", style = text(17, FontWeight.Normal, DashboardDim))
  }
}

/** Check display orientation from metadata without decoding every album image into memory. */
private fun isPortraitDashboardPhoto(path: String): Boolean = runCatching {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
  val rotation = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
  if (rotation % 180 == 0) bounds.outHeight > bounds.outWidth else bounds.outWidth > bounds.outHeight
}.getOrDefault(false)

/** Decode directly from disk with bounded memory and the phone camera's EXIF orientation. */
private fun decodeDashboardPhoto(path: String): Bitmap? = runCatching {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sample = 1
  while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
  val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
  val orientation = runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
  val matrix = Matrix()
  when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.preScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    else -> return bitmap
  }
  Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
}.getOrNull()

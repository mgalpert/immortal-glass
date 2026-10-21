/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Direct LAN control; no music credentials or extra server are stored on the Portal. */
@Composable
internal fun DashboardSonosCard(active: Boolean) {
  val context = LocalContext.current
  val pollSeconds = PortalOta.current?.providers?.get("sonos")?.first ?: 5
  val client = remember { SonosClient() }
  val network = remember { Mutex() }
  val scope = rememberCoroutineScope()
  val currentlyActive by rememberUpdatedState(active)
  var rooms by remember { mutableStateOf<List<SonosRoom>>(emptyList()) }
  var roomId by remember { mutableStateOf<String?>(null) }
  var snapshot by remember { mutableStateOf<SonosStatus?>(null) }
  var finding by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var disconnected by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var scan by remember { mutableIntStateOf(0) }
  var refresh by remember { mutableIntStateOf(0) }
  val room = rooms.firstOrNull { it.id == roomId } ?: rooms.firstOrNull()

  LaunchedEffect(active, scan) {
    if (active) while (true) {
      finding = rooms.isEmpty()
      try {
        val found = withContext(Dispatchers.IO) { network.withLock { discoverSonos(context, client) } }
        if (found.isNotEmpty()) rooms = found
      } finally { finding = false }
      delay(60_000)
    }
  }
  LaunchedEffect(active, room, refresh, pollSeconds) {
    snapshot = null
    disconnected = false
    if (active && room != null) while (true) {
      if (!busy) {
        val result = withContext(Dispatchers.IO) { network.withLock { runCatching { client.status(room) } } }
        result.onSuccess { snapshot = it; disconnected = false }
            .onFailure { snapshot = null; disconnected = true }
      }
      delay(pollSeconds * 1000L)
    }
  }
  val live = snapshot?.takeIf { it.room == room }
  fun <T> request(job: (SonosRoom) -> T, success: (T) -> Unit = {}) {
    val target = room ?: return
    if (!active || busy) return
    busy = true
    message = null
    scope.launch {
      try {
        val result = withContext(Dispatchers.IO) { network.withLock { job(target) } }
        if (currentlyActive) success(result)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        message = "Sonos couldn’t complete that. Try again."
      } finally {
        busy = false
        refresh++
      }
    }
  }
  fun chooseRoom() {
    val choices = rooms
    AlertDialog.Builder(context).setTitle("Sonos rooms")
        .setItems((choices.map { it.name } + "Find speakers again").toTypedArray()) { _, index ->
          if (index == choices.size) { scan++; refresh++ }
          else { roomId = choices[index].id; message = null }
        }.setNegativeButton("Close", null).show()
  }
  fun chooseFavorite() = request({ client.favorites(it) }) { favorites ->
    if (favorites.isEmpty()) {
      AlertDialog.Builder(context).setTitle("Sonos favorites")
          .setMessage("Save a Spotify playlist or station to Sonos Favorites in the Sonos app, then try again.")
          .setPositiveButton("OK", null).show()
    } else {
      AlertDialog.Builder(context).setTitle("Sonos favorites · ${room?.name.orEmpty()}")
          .setItems(favorites.map { it.title }.toTypedArray()) { _, index ->
            request({ client.playFavorite(it, favorites[index]) })
          }.setNegativeButton("Close", null).show()
    }
  }
  var art by remember { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(active, live?.artUrl) {
    art = null
    val url = live?.artUrl
    if (active && url != null) art = withContext(Dispatchers.IO) { MultiRoomArt.fetch(url) }
  }
  val ready = active && live != null && !busy
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = ::chooseRoom, enabled = active && !busy, modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text("Sonos · ${room?.name ?: if (finding) "Finding rooms…" else "Find speakers"} ⌄",
            Modifier.fillMaxWidth(), style = text(18, FontWeight.Medium, DashboardMint), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      SonosControl("Sonos favorites", "☆", active && room != null && !busy, ::chooseFavorite)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(DashboardMint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        val image = art
        if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("♫", style = text(30, color = DashboardMint))
      }
      Column(Modifier.weight(1f)) {
        val title = when {
          busy -> "One moment…"
          disconnected -> "Can’t reach this room"
          live != null -> live.title
          room != null -> "Connecting…"
          finding -> "Looking for Sonos…"
          else -> "No speakers found"
        }
        Text(title, style = text(22, FontWeight.Medium, DashboardInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(message ?: live?.artist?.takeIf { it.isNotBlank() }
            ?: if (disconnected) "Tap the room name to rescan" else "☆ Choose a favorite",
            style = text(15, FontWeight.Normal, DashboardDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      SonosControl("Previous track", "⏮", ready && live?.canPrevious == true) { request({ client.transport(it, "Previous") }) }
      SonosControl(if (live?.playing == true) "Pause Sonos" else "Play Sonos", if (live?.playing == true) "Ⅱ" else "▶", ready) {
        request({ client.transport(it, if (live?.playing == true) "Pause" else "Play") })
      }
      SonosControl("Next track", "⏭", ready && live?.canNext == true) { request({ client.transport(it, "Next") }) }
      Spacer(Modifier.weight(1f))
      SonosControl("Room volume down", "−", ready && live!!.volume > 0) { request({ client.setVolume(it, (live!!.volume - 5).coerceAtLeast(0)) }) }
      Text(live?.let { "${it.volume}%" } ?: "—", style = text(16, color = DashboardDim))
      SonosControl("Room volume up", "+", ready && live!!.volume < 100) { request({ client.setVolume(it, (live!!.volume + 5).coerceAtMost(100)) }) }
    }
  }
}

@Composable
private fun SonosControl(label: String, glyph: String, enabled: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp),
      modifier = Modifier.size(44.dp).semantics { contentDescription = label }) {
    Text(glyph, style = text(24, color = DashboardMint.copy(alpha = if (enabled) 1f else 0.3f)))
  }
}

@Suppress("DEPRECATION")
private fun discoverSonos(context: Context, client: SonosClient): List<SonosRoom> {
  val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  val multicast = wifi?.createMulticastLock("GlassSonosDiscovery")?.apply { setReferenceCounted(false) }
  return try {
    multicast?.acquire()
    val address = wifi?.connectionInfo?.ipAddress?.takeIf { it != 0 }?.let { ip ->
      InetAddress.getByAddress(ByteArray(4) { index -> (ip shr (8 * index)).toByte() })
    }
    client.discover(address)
  } catch (_: Exception) { emptyList() }
  finally { if (multicast?.isHeld == true) multicast.release() }
}

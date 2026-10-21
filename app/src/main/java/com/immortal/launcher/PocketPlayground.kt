/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val PocketInk get() = portalColor("pocketInk", 0xFF10365D)
internal val PocketMuted = Color(0xFF3F5267)
internal val PocketCream get() = portalColor("pocketCream", 0xFFFFF7E8)
internal val PocketCoral = Color(0xFFFF9B7F)
internal val PocketSky = Color(0xFFADE3F7)
internal val PocketMint = Color(0xFFAAEBD1)
internal val PocketLilac = Color(0xFFCEB4F6)
internal val PocketBlue = Color(0xFF9CDEEE)
internal val PocketYellow = Color(0xFFFFDA72)

/** Ticket notches are proportional so both orientations retain a readable inset. */
internal val PocketTicketShape: Shape = GenericShape { size, _ ->
  val r = minOf(size.width, size.height) * .10f
  moveTo(r, 0f)
  lineTo(size.width - r, 0f)
  quadraticTo(size.width - r, r, size.width, r)
  lineTo(size.width, size.height * .38f)
  cubicTo(size.width - r, size.height * .38f, size.width - r, size.height * .62f, size.width, size.height * .62f)
  lineTo(size.width, size.height - r)
  quadraticTo(size.width - r, size.height - r, size.width - r, size.height)
  lineTo(r, size.height)
  quadraticTo(r, size.height - r, 0f, size.height - r)
  lineTo(0f, size.height * .62f)
  cubicTo(r, size.height * .62f, r, size.height * .38f, 0f, size.height * .38f)
  lineTo(0f, r)
  quadraticTo(r, r, r, 0f)
  close()
}

@Composable
internal fun PocketPanel(color: Color, modifier: Modifier, shape: Shape, padding: Dp = 24.dp,
    onClick: (() -> Unit)? = null, clickLabel: String? = null, content: @Composable BoxScope.() -> Unit) {
  Box(modifier.shadow(7.dp, shape, ambientColor = Color(0xFF936B45), spotColor = Color(0xFF936B45))
      .clip(shape).background(Brush.verticalGradient(listOf(color, lerp(color, Color.White, .16f))))
      .border(3.dp, Color.White.copy(alpha = .55f), shape)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier)
      .padding(padding), content = content)
}

@Composable
internal fun PocketBadge(icon: String, label: String, modifier: Modifier = Modifier) {
  Row(modifier.background(Color.White.copy(alpha = .42f), RoundedCornerShape(28.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(icon, style = text(32, color = PocketInk))
    Text(label, style = text(24, FontWeight.ExtraBold, PocketInk), maxLines = 2)
  }
}

@Composable
internal fun PocketButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
  Button(onClick, modifier.heightIn(min = 64.dp), shape = RoundedCornerShape(32.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFCF3), contentColor = PocketInk),
      elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
      contentPadding = PaddingValues(horizontal = if (compact) 14.dp else 20.dp, vertical = 10.dp)) {
    Text(label, style = text(24, FontWeight.ExtraBold, PocketInk), maxLines = 2)
  }
}

private val LocalPocketArt = staticCompositionLocalOf<Map<Int, ImageBitmap>> { emptyMap() }
private val pocketArtSpecs = listOf(
    R.drawable.pocket_dog to 384, R.drawable.pocket_planet to 384,
    R.drawable.pocket_puzzle to 384, R.drawable.pocket_science to 384,
    R.drawable.pocket_garden to 1920,
)

/** Board and lesson windows share five sampled images for the lifetime of this face. */
@Composable
internal fun PocketArtHost(content: @Composable () -> Unit) {
  val resources = LocalResources.current
  val configuration = LocalConfiguration.current
  val release = PortalOta.current
  val remoteIds = mapOf(R.drawable.pocket_dog to "dog", R.drawable.pocket_planet to "planet",
      R.drawable.pocket_puzzle to "puzzle", R.drawable.pocket_science to "science", R.drawable.pocket_garden to "garden")
  val art by produceState<Map<Int, ImageBitmap>>(emptyMap(), resources, configuration, release?.revision) {
    coroutineScope {
      pocketArtSpecs.forEach { (id, edge) -> launch {
        val decoded = withContext(Dispatchers.Default) {
          fun decode(file: java.io.File?): ImageBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file != null) BitmapFactory.decodeFile(file.path, bounds) else BitmapFactory.decodeResource(resources, id, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
              inScaled = false
              var sample = 1
              while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
              inSampleSize = sample
            }
            return (if (file != null) BitmapFactory.decodeFile(file.path, options) else BitmapFactory.decodeResource(resources, id, options))?.asImageBitmap()
          }
          val remote = release?.asset(remoteIds[id] ?: "")
          decode(remote) ?: if (remote != null) decode(null) else null
        }
        if (decoded != null) value = value + (id to decoded)
      } }
    }
  }
  CompositionLocalProvider(LocalPocketArt provides art, content = content)
}

@Composable
internal fun PocketArtwork(@DrawableRes id: Int, modifier: Modifier, description: String? = null, scale: ContentScale = ContentScale.Fit) {
  val art = LocalPocketArt.current[id]
  if (art != null) {
    Image(art, description, modifier, contentScale = scale)
  } else {
    Spacer(modifier)
  }
}

@Composable
internal fun PocketHeart(modifier: Modifier) {
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val heart = Path().apply {
      moveTo(w * .5f, h * .88f)
      cubicTo(-w * .12f, h * .48f, w * .02f, -h * .12f, w * .5f, h * .21f)
      cubicTo(w * .98f, -h * .12f, w * 1.12f, h * .48f, w * .5f, h * .88f)
    }
    drawPath(heart, Brush.verticalGradient(listOf(Color(0xFFFFA7B5), Color(0xFFF2688A))))
    drawCircle(PocketInk, w * .032f, Offset(w * .36f, h * .43f))
    drawCircle(PocketInk, w * .032f, Offset(w * .64f, h * .43f))
    val smile = Path().apply { moveTo(w * .42f, h * .53f); quadraticTo(w * .5f, h * .64f, w * .58f, h * .53f) }
    drawPath(smile, PocketInk, style = Stroke(w * .03f))
  }
}

@Composable
internal fun PocketConversation(modifier: Modifier) {
  Canvas(modifier) {
    val w = size.width; val h = size.height
    fun bubble(x: Float, y: Float, r: Float, color: Color) {
      drawCircle(color, r, Offset(x, y))
      val tail = Path().apply { moveTo(x - r * .4f, y + r * .5f); lineTo(x - r * .9f, y + r * 1.1f); lineTo(x + r * .05f, y + r * .8f); close() }
      drawPath(tail, color)
    }
    bubble(w * .38f, h * .38f, w * .34f, Color(0xFF77C9F3))
    bubble(w * .66f, h * .64f, w * .29f, Color(0xFFFFA6B3))
    for (x in listOf(.28f, .40f, .52f)) drawCircle(PocketInk.copy(alpha = .45f), w * .025f, Offset(w * x, h * .37f))
  }
}

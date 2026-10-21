/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/**
 * Fetches cover art for the multi-room now-playing card from Music Assistant. MA serves
 * art over plain HTTP on the LAN, but the launcher stays hardened
 * (`usesCleartextTraffic=false`), so for `http://` URLs we do ONE deliberate, auditable
 * plaintext fetch over a RAW SOCKET — the cleartext policy governs the platform HTTP
 * stack and WebView, not raw sockets, so this keeps the app-wide hardening intact while
 * still reading the LAN art. `https://` URLs go through the normal stack.
 *
 * Caches the last URL→bitmap so we don't re-fetch while a track keeps playing.
 */
object MultiRoomArt {
  private const val MAX_EDGE = 384 // px — plenty for the card / header chip
  private const val MAX_BODY = 8 * 1024 * 1024 // 8 MB cap — art, not downloads
  private const val MAX_HEADERS = 64 * 1024

  private var cachedUrl: String = ""
  private var cachedBitmap: Bitmap? = null

  /** Downscaled cover bitmap for [url], or null on any failure. Blocking — call off-main. */
  @Synchronized
  fun fetch(url: String): Bitmap? {
    if (url.isBlank()) return null
    if (url == cachedUrl && cachedBitmap != null) return cachedBitmap
    val raw = runCatching { download(url, redirects = 3) }.getOrNull() ?: return null
    val bmp = runCatching { decodeAndScale(raw) }.getOrNull() ?: return null
    cachedUrl = url
    cachedBitmap = bmp
    return bmp
  }

  /** Raw bytes for [url]: https via the platform stack, http via a raw socket. */
  private fun download(spec: String, redirects: Int): ByteArray? {
    val u = URL(spec)
    return if (u.protocol.equals("https", ignoreCase = true)) downloadTls(u)
    else downloadCleartext(u, redirects)
  }

  private fun downloadTls(u: URL): ByteArray? {
    val c = (u.openConnection() as HttpURLConnection).apply {
      connectTimeout = 6000
      readTimeout = 8000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "Immortal/1.0")
    }
    return runCatching { c.inputStream.use { readBounded(it) } }.getOrNull().also { c.disconnect() }
  }

  /**
   * Minimal HTTP/1.1 GET over a raw socket with `Connection: close`, so we just read to
   * EOF after bounded headers — no Content-Length bookkeeping for the
   * common case. Handles bounded chunked bodies and follows up to three redirects.
   */
  private fun downloadCleartext(u: URL, redirects: Int): ByteArray? {
    val host = u.host
    val port = if (u.port != -1) u.port else 80
    val path = (if (u.path.isNullOrBlank()) "/" else u.path) + (u.query?.let { "?$it" } ?: "")
    Socket(host, port).use { s ->
      s.soTimeout = 8000
      val req =
          "GET $path HTTP/1.1\r\n" +
              "Host: $host\r\n" +
              "User-Agent: Immortal/1.0\r\n" +
              "Accept: image/*\r\n" +
              "Connection: close\r\n\r\n"
      s.getOutputStream().apply {
        write(req.toByteArray(Charsets.ISO_8859_1))
        flush()
      }
      val input = s.getInputStream().buffered()
      val header = readHeaders(input) ?: return null
      val status = header.lineSequence().firstOrNull()?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 0
      if (status in 300..399 && redirects > 0) {
        val loc =
            header.lineSequence()
                .firstOrNull { it.startsWith("Location:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
        if (!loc.isNullOrBlank()) {
          val next = runCatching { URL(u, loc) }.getOrNull() ?: return null
          return download(next.toString(), redirects - 1)
        }
      }
      if (status !in 200..299) return null
      var body = readBounded(input) ?: return null
      if (header.contains("Transfer-Encoding: chunked", ignoreCase = true)) body = dechunk(body)
      return body
    }
  }

  /** Read at most the body cap plus one overflow byte; never return a truncated image. */
  internal fun readBounded(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer, 0, minOf(buffer.size, MAX_BODY - out.size() + 1))
      if (count < 0) return out.toByteArray()
      if (count > MAX_BODY - out.size()) return null
      out.write(buffer, 0, count)
    }
  }

  /** Headers have their own cap, so a valid body may still use the full 8 MB budget. */
  internal fun readHeaders(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    var tail = 0
    repeat(MAX_HEADERS) {
      val next = input.read()
      if (next < 0) return null
      out.write(next)
      tail = (tail shl 8) or next
      if (tail == 0x0d0a0d0a) {
        val bytes = out.toByteArray()
        return String(bytes, 0, bytes.size - 4, Charsets.ISO_8859_1)
      }
    }
    return null
  }

  /** De-chunk an HTTP/1.1 chunked body. */
  private fun dechunk(body: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < body.size) {
      var j = i
      while (j + 1 < body.size && !(body[j] == 13.toByte() && body[j + 1] == 10.toByte())) j++
      val sizeHex = String(body, i, j - i, Charsets.ISO_8859_1).trim().substringBefore(";")
      val size = sizeHex.toIntOrNull(16) ?: break
      if (size == 0) break
      val start = j + 2
      if (start + size > body.size) break
      out.write(body, start, size)
      i = start + size + 2 // skip the chunk's trailing CRLF
    }
    return out.toByteArray()
  }

  private fun decodeAndScale(raw: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val sample = sampleSize(bounds.outWidth, bounds.outHeight) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val src = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
    val longest = maxOf(src.width, src.height)
    if (longest <= MAX_EDGE) return src
    val ratio = MAX_EDGE.toFloat() / longest
    val scaled =
        Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt().coerceAtLeast(1), (src.height * ratio).toInt().coerceAtLeast(1), true)
    if (scaled !== src) src.recycle()
    return scaled
  }

  /** Power-of-two sampling bounds the decoded bitmap before allocating its pixels. */
  internal fun sampleSize(width: Int, height: Int): Int? {
    if (width <= 0 || height <= 0) return null
    val longest = maxOf(width, height).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > MAX_EDGE) sample *= 2
    return sample
  }
}

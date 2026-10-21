/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class MultiRoomArtTest {
  private val bodyCap = 8 * 1024 * 1024
  private val headerCap = 64 * 1024

  /** An arbitrarily long response without allocating that response in the test. */
  private class ResponseStream(private val length: Long) : InputStream() {
    var consumed = 0L
      private set

    override fun read(): Int = if (consumed == length) -1 else {
      consumed++
      42
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
      if (consumed == this.length) return -1
      val count = minOf(length.toLong(), this.length - consumed).toInt()
      buffer.fill(42, offset, offset + count)
      consumed += count
      return count
    }
  }

  @Test fun responseAtBodyLimitIsCompleteAndNotTruncated() {
    val input = ResponseStream(bodyCap.toLong())
    val body = MultiRoomArt.readBounded(input)
    assertNotNull(body)
    assertEquals(bodyCap, body!!.size)
    assertEquals(42.toByte(), body.first())
    assertEquals(42.toByte(), body.last())
    assertEquals(bodyCap.toLong(), input.consumed)
  }

  @Test fun oneByteOverBodyLimitRejectsTheEntireResponse() {
    val input = ResponseStream(bodyCap.toLong() + 1)
    assertNull(MultiRoomArt.readBounded(input))
    assertEquals(bodyCap.toLong() + 1, input.consumed)
  }

  @Test fun unboundedResponseStopsAfterOneOverflowByte() {
    val input = ResponseStream(Long.MAX_VALUE)
    assertNull(MultiRoomArt.readBounded(input))
    assertEquals(bodyCap.toLong() + 1, input.consumed)
  }

  @Test fun shortResponsePreservesEveryByte() {
    val bytes = byteArrayOf(0, 1, 127, -1, 42)
    assertArrayEquals(bytes, MultiRoomArt.readBounded(ByteArrayInputStream(bytes)))
    assertArrayEquals(byteArrayOf(), MultiRoomArt.readBounded(ByteArrayInputStream(byteArrayOf())))
  }

  @Test fun maximumSizeHeadersLeaveTheCompleteBodyBudgetAvailable() {
    val headers = "HTTP/1.1 200 OK\r\nX-Padding: " +
        "a".repeat(headerCap - "HTTP/1.1 200 OK\r\nX-Padding: ".length - 4) + "\r\n\r\n"
    val body = ResponseStream(bodyCap.toLong())
    val input = java.io.SequenceInputStream(ByteArrayInputStream(headers.toByteArray()), body)
    assertEquals(headers.dropLast(4), MultiRoomArt.readHeaders(input))
    assertEquals(bodyCap, MultiRoomArt.readBounded(input)!!.size)
    assertEquals(bodyCap.toLong(), body.consumed)
  }

  @Test fun oversizedHeadersStopBeforeReadingAnUnboundedResponse() {
    val input = ResponseStream(Long.MAX_VALUE)
    assertNull(MultiRoomArt.readHeaders(input))
    assertEquals(headerCap.toLong(), input.consumed)
  }

  @Test fun incompleteHeaderDoesNotTreatItsBytesAsImageData() {
    assertNull(MultiRoomArt.readHeaders(ByteArrayInputStream("HTTP/1.1 200 OK\r\n".toByteArray())))
    assertNull(MultiRoomArt.readHeaders(ByteArrayInputStream(byteArrayOf())))
  }

  @Test fun samplingKeepsExcessiveDimensionsWithinThePixelBudget() {
    val dimensions = listOf(
        384 to 384, 385 to 384, 4000 to 3000, 3000 to 4000,
        100_000 to 100_000, Int.MAX_VALUE to Int.MAX_VALUE,
        Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)
    for ((width, height) in dimensions) {
      val sample = MultiRoomArt.sampleSize(width, height)!!
      assertTrue("decoder requires a positive power of two", sample > 0 && (sample and (sample - 1)) == 0)
      // Round up: the decoder may retain a final partial row/column.
      val decodedWidth = (width.toLong() + sample - 1) / sample
      val decodedHeight = (height.toLong() + sample - 1) / sample
      assertTrue("$width x $height decoded width", decodedWidth in 1L..384L)
      assertTrue("$width x $height decoded height", decodedHeight in 1L..384L)
      assertTrue("$width x $height decoded pixel budget", decodedWidth * decodedHeight <= 384L * 384L)
    }
  }

  @Test fun smallImagesKeepTheirOriginalResolution() {
    for ((width, height) in listOf(1 to 1, 256 to 128, 384 to 384)) {
      assertEquals(1, MultiRoomArt.sampleSize(width, height))
    }
  }

  @Test fun invalidImageDimensionsCannotReachPixelDecode() {
    for ((width, height) in listOf(-1 to -1, 0 to 4000, 4000 to 0, -1 to 100, 100 to -1,
        Int.MIN_VALUE to Int.MAX_VALUE)) {
      assertNull(MultiRoomArt.sampleSize(width, height))
    }
  }
}

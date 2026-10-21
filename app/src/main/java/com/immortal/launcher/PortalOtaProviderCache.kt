/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.io.File
import org.json.JSONObject

/** Public provider payload plus receipt time; observation time remains inside the source payload. */
internal class PortalOtaProviderCache(private val root: File) {
  init { root.mkdirs() }
  @Synchronized fun put(key: String, body: String, fetchedAt: Long = System.currentTimeMillis()) {
    otaCheck(key.matches(Regex("[a-z0-9-]{1,60}")))
    otaCheck(body.toByteArray().size <= 1024 * 1024, "provider_too_large")
    PortalOtaStore.atomicWrite(File(root, "$key.json"), JSONObject().put("fetchedAt", fetchedAt).put("body", body).toString())
  }
  @Synchronized fun get(key: String): Pair<String, Long>? = runCatching {
    otaCheck(key.matches(Regex("[a-z0-9-]{1,60}")))
    val file = File(root, "$key.json")
    if (!file.isFile || file.length() > 2 * 1024 * 1024) return null
    val json = JSONObject(file.readText())
    json.getString("body") to json.getLong("fetchedAt")
  }.getOrNull()
}

/** Stable photo identity survives a native layout reorder or content revision. */
internal object PortalPhotoCursor { @Volatile var path: String? = null }

/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Strict RFC 4648 decoder, including padding bits; works on the app's API 24 minimum. */
internal object PortalBase64 {
  private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  fun decode(raw: String): ByteArray {
    otaCheck(raw.length % 4 == 0 && Regex("[A-Za-z0-9+/]*={0,2}").matches(raw), "invalid_base64")
    if (raw.isEmpty()) return ByteArray(0)
    val padding = raw.takeLast(2).count { it == '=' }
    val output = ByteArray(raw.length / 4 * 3 - padding)
    var cursor = 0
    for (i in raw.indices step 4) {
      val a = alphabet.indexOf(raw[i]); val b = alphabet.indexOf(raw[i + 1])
      val c = if (raw[i + 2] == '=') 0 else alphabet.indexOf(raw[i + 2])
      val d = if (raw[i + 3] == '=') 0 else alphabet.indexOf(raw[i + 3])
      otaCheck(a >= 0 && b >= 0 && c >= 0 && d >= 0, "invalid_base64")
      if (i == raw.length - 4) {
        if (padding == 2) otaCheck(b and 15 == 0, "invalid_base64")
        if (padding == 1) otaCheck(c and 3 == 0, "invalid_base64")
      }
      val bits = (a shl 18) or (b shl 12) or (c shl 6) or d
      if (cursor < output.size) output[cursor++] = (bits shr 16).toByte()
      if (cursor < output.size) output[cursor++] = (bits shr 8).toByte()
      if (cursor < output.size) output[cursor++] = bits.toByte()
    }
    return output
  }
}

/** Only symbolic codes cross the acknowledgment/health boundary. */
internal class PortalOtaError(val code: String) : Exception(code)
internal fun otaCheck(value: Boolean, code: String = "invalid_manifest") { if (!value) throw PortalOtaError(code) }
internal fun otaHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal data class PortalAsset(val id: String, val hash: String, val bytes: Long, val mime: String)
internal data class PortalDay(val id: String, val date: String, val fact: EveningFact, val word: EveningWord,
    val puzzle: EveningQuestion, val science: EveningQuestion, val kindness: String, val reflection: String, val quoteId: String)
internal data class PortalPackage(val revision: Long, val activateAt: Long, val validUntil: Long,
    val days: Map<String, PortalDay>, val quotes: Map<String, String>, val events: Map<String, List<DashboardKidEvent>>,
    val assets: List<PortalAsset>, val theme: Map<String, String>, val order: List<Int>,
    val providers: Map<String, Pair<Int, Int>>, val desiredSettings: JSONObject, val envelope: String,
    val directory: File? = null) {
  fun day(now: Long) = days[PortalOtaProtocol.localDay(now)]
  fun asset(id: String): File? = assets.firstOrNull { it.id == id }?.let { a -> directory?.resolve("assets/${a.hash}")?.takeIf { it.isFile } }
}

/** Pure contract boundary: authenticate the exact payload bytes before parsing any content. */
internal object PortalOtaProtocol {
  const val MAX_MANIFEST = 2 * 1024 * 1024
  const val MAX_ASSET = 32L * 1024 * 1024
  const val MAX_TOTAL = 256L * 1024 * 1024
  val types = setOf("weather", "transit", "clockAlarm", "photos", "kidsEvents", "sonos", "muse", "fact", "englishWord", "spanishWord", "puzzle", "science", "kindness", "family")
  val actions = setOf("openLesson", "revealAnswer", "cycleQuote", "cyclePhoto", "localAlarm", "sonosControl", "openMuse")
  private val utc = TimeZone.getTimeZone("UTC")
  private val local = TimeZone.getTimeZone("America/Los_Angeles")
  fun localDay(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = local }.format(Date(at))
  fun timestamp(raw: String): Long {
    otaCheck(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z").matches(raw), "invalid_timestamp")
    val fraction = raw.substringAfter('.', "").removeSuffix("Z")
    val normalized = if ('.' in raw) raw.substringBefore('.') + "." + fraction.take(3).padEnd(3, '0') + "Z" else raw
    val format = SimpleDateFormat(if ('.' in normalized) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = utc; isLenient = false }
    return runCatching { format.parse(normalized)!!.time }.getOrElse { throw PortalOtaError("invalid_timestamp") }
  }
  fun date(raw: String): Long {
    otaCheck(Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw), "invalid_date")
    return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = local; isLenient = false }.parse(raw)!!.time }
        .getOrElse { throw PortalOtaError("invalid_date") }
  }
  fun publicKey(encoded: String): PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(PortalBase64.decode(encoded)))
  fun verify(envelope: String, keys: Map<String, PublicKey>, appVersion: Long): PortalPackage {
    otaCheck(envelope.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST * 2, "manifest_too_large")
    val e = JSONObject(envelope)
    val key = keys[e.getString("keyId")] ?: throw PortalOtaError("unknown_signing_key")
    val bytes = runCatching { PortalBase64.decode(e.getString("payload")) }.getOrElse { throw PortalOtaError("invalid_signature") }
    otaCheck(bytes.size <= MAX_MANIFEST, "manifest_too_large")
    val signed = runCatching { Signature.getInstance("SHA256withRSA").run { initVerify(key); update(bytes); verify(PortalBase64.decode(e.getString("signature"))) } }.getOrDefault(false)
    otaCheck(signed, "invalid_signature")
    return parse(JSONObject(bytes.toString(Charsets.UTF_8)), appVersion, envelope)
  }
  private fun JSONObject.s(key: String, max: Int = 6000, empty: Boolean = false): String {
    val value = get(key)
    otaCheck(value is String)
    val str = value as String
    otaCheck(str.length <= max && (empty || str.isNotBlank()))
    return str
  }
  private fun JSONObject.n(key: String): Long {
    val value = get(key)
    otaCheck(value is Number && value.toString().matches(Regex("[0-9]+")))
    return (value as Number).toLong()
  }
  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
  private fun source(j: JSONObject) { val url = j.s("sourceUrl", 6000, true); otaCheck(url.isEmpty() || (java.net.URI(url).run { scheme == "https" && host != null && userInfo == null })) }
  private fun question(j: JSONObject): EveningQuestion { source(j); return EveningQuestion(j.s("prompt", 240), j.s("answer"), j.s("sourceName", 6000, true), j.s("sourceUrl", 6000, true), j.s("detail", empty = true)) }
  fun parse(j: JSONObject, appVersion: Long, envelope: String = ""): PortalPackage {
    otaCheck(j.n("schemaVersion") == 1L && j.n("componentVersion") == 1L, "unsupported_schema")
    otaCheck(j.n("minAppVersion") in 1L..appVersion, "incompatible_app")
    val revision = j.n("revision"); otaCheck(revision > 0); j.s("id", 128)
    listOf("createdAt", "editedAt", "publishedAt").forEach { timestamp(j.s(it, 40)) }
    val activate = timestamp(j.s("activateAt", 40)); val until = timestamp(j.s("validUntil", 40)); otaCheck(until > activate)
    otaCheck(j.s("timezone", 80) == "America/Los_Angeles", "unsupported_timezone")
    val themeJson = j.getJSONObject("theme")
    val theme = listOf("dashboardBackground", "dashboardInk", "pocketInk", "pocketCream").associateWith { key -> themeJson.s(key, 7).also { otaCheck(Regex("#[0-9a-fA-F]{6}").matches(it)) } }
    val layout = j.getJSONObject("layouts")
    otaCheck(layout.s("dashboard", 40) == "family-04" && layout.s("discover", 40) == "pocket-playground", "unsupported_layout")
    val orderJson = layout.getJSONArray("dashboardOrder")
    val order = (0 until orderJson.length()).map { i -> val n = orderJson.get(i); otaCheck(n is Int); n as Int }
    otaCheck(order.size == 8 && order.toSet() == (0..7).toSet(), "unsupported_layout")
    val components = j.getJSONArray("components"); otaCheck(components.length() in 1..64)
    val componentIds = mutableSetOf<String>()
    components.objects().forEach { c ->
      otaCheck(componentIds.add(c.s("id", 64)))
      otaCheck(c.s("type", 40) in types && c.n("version") == 1L, "unsupported_component")
      val a = c.getJSONArray("actions"); otaCheck(a.length() <= actions.size)
      (0 until a.length()).forEach { otaCheck(a.get(it) is String && a.getString(it) in actions, "unsupported_action") }
    }
    val providerJson = j.getJSONObject("providers")
    val providers = listOf("weather", "transit", "sonos", "photos").associateWith { name ->
      val p = providerJson.getJSONObject(name); val refresh = p.n("refreshSeconds")
      val range = when(name) { "weather" -> 900L..1800L; "transit" -> 20L..30L; "sonos" -> 3L..30L; else -> 10L..600L }
      otaCheck(refresh in range)
      val stale = if (name in setOf("weather", "transit")) p.n("staleSeconds") else refresh * 3
      if (name == "weather") otaCheck(stale in 900L..10800L)
      if (name == "transit") otaCheck(stale in 30L..120L)
      refresh.toInt() to stale.toInt()
    }
    val assetJson = j.getJSONArray("assets"); otaCheck(assetJson.length() <= 256)
    val assets = assetJson.objects().map { a ->
      val hash = a.s("sha256", 64); otaCheck(Regex("[a-f0-9]{64}").matches(hash), "invalid_asset")
      val size = a.n("bytes"); otaCheck(size in 1..MAX_ASSET, "asset_too_large")
      val mime = a.s("mime", 80); otaCheck(mime in setOf("image/png", "image/jpeg", "image/webp"), "unsupported_asset")
      a.s("originalName", 256); a.s("source", 1024); timestamp(a.s("createdAt", 40))
      PortalAsset(a.s("id", 64), hash, size, mime)
    }
    otaCheck(assets.map { it.id }.toSet().size == assets.size && assets.sumOf { it.bytes } <= MAX_TOTAL, "invalid_asset")
    val quoteJson = j.getJSONArray("quotes"); otaCheck(quoteJson.length() in 1..64)
    val quotes = linkedMapOf<String, String>()
    quoteJson.objects().forEach { q -> otaCheck(quotes.put(q.s("id", 64), q.s("text", 160)) == null) }
    val dayJson = j.getJSONArray("days"); otaCheck(dayJson.length() in 30..400)
    val days = linkedMapOf<String, PortalDay>(); val dayIds = mutableSetOf<String>()
    dayJson.objects().forEach { d ->
      val day = d.s("date", 10); date(day); otaCheck(d.get("approved") == true, "unapproved_day")
      val id = d.s("id", 64); otaCheck(dayIds.add(id)); val f = d.getJSONObject("fact"); source(f)
      val fact = EveningFact(f.s("title", 80), f.s("body", 240), f.s("sourceName"), f.s("sourceUrl"), f.s("detail"), f.s("tryIt"))
      val w = d.getJSONObject("word")
      val word = EveningWord(w.s("english", 24), w.s("partOfSpeech", 32), w.s("definition", 120), w.s("example", 240), w.s("spanish", 32), w.s("spanishExample", 240), w.s("spanishTranslation", 240), w.s("spanishDefinition", 160), w.s("practice"))
      val quoteId = d.s("quoteId", 64); otaCheck(quoteId in quotes)
      otaCheck(days.put(day, PortalDay(id, day, fact, word, question(d.getJSONObject("puzzle")), question(d.getJSONObject("science")), d.s("kindness", 160), d.s("reflection", 160), quoteId)) == null)
    }
    val cursor = Calendar.getInstance(local).apply { timeInMillis = date(localDay(activate)) }
    repeat(30) { otaCheck(localDay(cursor.timeInMillis) in days, "insufficient_horizon"); cursor.add(Calendar.DAY_OF_YEAR, 1) }
    val eventJson = j.getJSONObject("events"); otaCheck(eventJson.length() <= 400)
    val events = eventJson.keys().asSequence().associateWith { day ->
      date(day); val a = eventJson.getJSONArray(day); otaCheck(a.length() <= 4)
      val ids = mutableSetOf<String>()
      a.objects().map { ev -> otaCheck(ids.add(ev.s("id", 64))); DashboardKidEvent(ev.s("name", 32), ev.s("activity", 48), if (ev.isNull("time") && ev.has("time")) null else ev.s("time", 16, true)) }
    }
    val settings = j.getJSONObject("desiredSettings")
    otaCheck(settings.n("version") == 1L, "unsupported_settings"); settings.n("baseLocalVersion")
    val values = settings.getJSONObject("values")
    otaCheck(values.keys().asSequence().all { it in setOf("enabled", "pollSeconds") }, "unsupported_settings")
    if (values.has("enabled")) otaCheck(values.get("enabled") is Boolean, "invalid_settings")
    if (values.has("pollSeconds")) otaCheck(values.n("pollSeconds") in 60L..3600L, "invalid_settings")
    return PortalPackage(revision, activate, until, days, quotes, events, assets, theme, order, providers, settings, envelope)
  }
}

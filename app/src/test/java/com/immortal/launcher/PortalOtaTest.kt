/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.immortal.launcher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.Calendar
import java.util.TimeZone

class PortalOtaTest {
  private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
  private val keys = mapOf("test" to pair.public)
  private val now = PortalOtaProtocol.timestamp("2026-10-20T19:00:00Z")
  private val image = byteArrayOf(1, 2, 3, 4)
  private fun fixture(revision: Int = 1, activation: String = "2026-10-20T07:00:00Z"): JSONObject {
    val question = JSONObject().put("prompt", "Why?").put("answer", "Because.").put("sourceName", "NASA").put("sourceUrl", "https://science.nasa.gov/").put("detail", "Explanation")
    val fact = JSONObject().put("title", "Our star").put("body", "The Sun is a star.").put("sourceName", "NASA").put("sourceUrl", "https://science.nasa.gov/").put("detail", "More").put("tryIt", "Imagine")
    val word = JSONObject(); listOf("english", "partOfSpeech", "definition", "example", "spanish", "spanishExample", "spanishTranslation", "spanishDefinition", "practice").forEach { word.put(it, "Curious") }
    val days = JSONArray()
    val cursor = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).apply { timeInMillis = PortalOtaProtocol.date(PortalOtaProtocol.localDay(PortalOtaProtocol.timestamp(activation))) }
    repeat(35) {
      val date = PortalOtaProtocol.localDay(cursor.timeInMillis)
      days.put(JSONObject().put("date", date).put("id", "day-$date").put("approved", true).put("fact", fact).put("word", word).put("puzzle", question).put("science", question).put("kindness", "Listen.").put("reflection", "What did you learn?").put("quoteId", "q1"))
      cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    return JSONObject().put("schemaVersion", 1).put("componentVersion", 1).put("revision", revision).put("id", "r$revision")
        .put("createdAt", "2026-10-19T00:00:00Z").put("editedAt", "2026-10-19T00:00:00Z").put("publishedAt", "2026-10-19T00:00:00Z")
        .put("activateAt", activation).put("validUntil", "2027-12-01T00:00:00Z").put("timezone", "America/Los_Angeles").put("minAppVersion", 68)
        .put("theme", JSONObject().put("dashboardBackground", "#101216").put("dashboardInk", "#F8F5FF").put("pocketInk", "#10365D").put("pocketCream", "#FFF7E8"))
        .put("layouts", JSONObject().put("dashboard", "family-04").put("discover", "pocket-playground").put("dashboardOrder", JSONArray((0..7).toList())))
        .put("components", JSONArray().put(JSONObject().put("id", "fact").put("type", "fact").put("version", 1).put("actions", JSONArray().put("openLesson"))))
        .put("providers", JSONObject().put("weather", JSONObject().put("refreshSeconds", 900).put("staleSeconds", 5400)).put("transit", JSONObject().put("refreshSeconds", 30).put("staleSeconds", 120)).put("photos", JSONObject().put("refreshSeconds", 30)).put("sonos", JSONObject().put("refreshSeconds", 5)))
        .put("assets", JSONArray().put(JSONObject().put("id", "dog").put("sha256", otaHash(image)).put("bytes", image.size).put("mime", "image/png").put("originalName", "dog.png").put("source", "Owner artwork").put("createdAt", "2026-10-19T00:00:00Z")))
        .put("quotes", JSONArray().put(JSONObject().put("id", "q1").put("text", "Keep trying."))).put("days", days)
        .put("events", JSONObject().put("2026-10-20", JSONArray().put(JSONObject().put("id", "e1").put("name", "Tenzin").put("activity", "soccer").put("time", JSONObject.NULL))))
        .put("desiredSettings", JSONObject().put("version", 1).put("baseLocalVersion", 0).put("values", JSONObject()))
  }
  private fun envelope(json: JSONObject, pretty: Boolean = false): String {
    val bytes = (if (pretty) json.toString(2) else json.toString()).toByteArray()
    val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(bytes); sign() }
    return JSONObject().put("keyId", "test").put("payload", Base64.getEncoder().encodeToString(bytes)).put("signature", Base64.getEncoder().encodeToString(signature)).toString()
  }
  private fun pack(revision: Int = 1) = PortalOtaProtocol.verify(envelope(fixture(revision)), keys, 69)
  private fun rejected(code: String? = null, block: () -> Unit) {
    try { block(); fail("Expected rejection") } catch (e: PortalOtaError) { if (code != null) assertEquals(code, e.code) }
  }
  @Test fun base64IsCanonicalAndWorksWithoutApi26Codec() {
    val random = java.util.Random(42)
    (0..257).forEach { size ->
      val bytes = ByteArray(size).also { random.nextBytes(it) }
      assertArrayEquals(bytes, PortalBase64.decode(Base64.getEncoder().encodeToString(bytes)))
    }
    listOf("AA=A", "AA===", "AB==", "AAB=", "AAA", "@@@@").forEach { invalid ->
      rejected("invalid_base64") { PortalBase64.decode(invalid) }
    }
  }
  @Test fun otaSettingsRegistryCoversCadenceAndReadonlyLocalVersion() {
    val domain = com.immortal.launcher.settings.SettingsDomains.portal
    assertEquals(setOf("enabled", "pollSeconds", "localVersion"), domain.specs.map { it.key }.toSet())
    val json = domain.flatJson(PortalOtaConfig.Settings())
    assertTrue(json.getBoolean("enabled")); assertEquals(60, json.getInt("pollSeconds")); assertEquals(0L, json.getLong("localVersion"))
  }
  @Test fun actualMacPublisherEnvelopeInteroperates() {
    val path = System.getenv("PORTAL_CROSS_FIXTURE")
    org.junit.Assume.assumeNotNull(path)
    val dir = java.io.File(path!!)
    val keyJson = JSONObject(dir.resolve("public-keys.json").readText())
    val macKeys = keyJson.keys().asSequence().associateWith { PortalOtaProtocol.publicKey(keyJson.getString(it)) }
    val release = PortalOtaProtocol.verify(dir.resolve("envelope.json").readText(), macKeys, 69)
    assertTrue(release.revision > 0); assertTrue(release.days.size >= 30)
  }
  @Test fun verifiesExactSignedBytesWithoutReserializing() {
    assertEquals(1L, PortalOtaProtocol.verify(envelope(fixture(), true), keys, 69).revision)
    val signed = JSONObject(envelope(fixture()))
    val changed = String(Base64.getDecoder().decode(signed.getString("payload"))).replace("Keep trying.", "Changed.")
    signed.put("payload", Base64.getEncoder().encodeToString(changed.toByteArray()))
    rejected("invalid_signature") { PortalOtaProtocol.verify(signed.toString(), keys, 69) }
    rejected("unknown_signing_key") { PortalOtaProtocol.verify(envelope(fixture()), emptyMap(), 69) }
  }
  @Test fun rejectsUnsupportedSchemasActionsLayoutsVersionsAndUnsafeSettings() {
    rejected("unsupported_schema") { PortalOtaProtocol.parse(fixture().put("schemaVersion", 2), 69) }
    rejected("incompatible_app") { PortalOtaProtocol.parse(fixture().put("minAppVersion", 70), 69) }
    val a = fixture(); a.getJSONArray("components").getJSONObject(0).put("actions", JSONArray().put("runScript"))
    rejected("unsupported_action") { PortalOtaProtocol.parse(a, 69) }
    val b = fixture(); b.getJSONObject("layouts").put("dashboardOrder", JSONArray(listOf(0,0,1,2,3,4,5,6)))
    rejected("unsupported_layout") { PortalOtaProtocol.parse(b, 69) }
    val c = fixture(); c.getJSONObject("desiredSettings").getJSONObject("values").put("alarm", 12)
    rejected("unsupported_settings") { PortalOtaProtocol.parse(c, 69) }
    val d = fixture(); d.getJSONObject("desiredSettings").getJSONObject("values").put("pollSeconds", 59)
    rejected("invalid_settings") { PortalOtaProtocol.parse(d, 69) }
  }
  @Test fun horizonUsesCalendarDaysAcrossBothDstTransitions() {
    listOf("2026-10-20T07:00:00Z", "2026-03-01T08:00:00Z").forEach { activation ->
      val j = fixture(activation = activation)
      assertEquals(35, PortalOtaProtocol.parse(j, 69).days.size)
      j.getJSONArray("days").remove(12)
      rejected("insufficient_horizon") { PortalOtaProtocol.parse(j, 69) }
    }
    assertEquals("2026-11-01", PortalOtaProtocol.localDay(PortalOtaProtocol.timestamp("2026-11-01T08:30:00Z")))
    assertEquals("2026-11-01", PortalOtaProtocol.localDay(PortalOtaProtocol.timestamp("2026-11-01T09:30:00Z")))
    assertEquals("2026-03-08", PortalOtaProtocol.localDay(PortalOtaProtocol.timestamp("2026-03-08T09:59:00Z")))
    assertEquals("2026-03-08", PortalOtaProtocol.localDay(PortalOtaProtocol.timestamp("2026-03-08T10:01:00Z")))
    rejected("invalid_date") { PortalOtaProtocol.date("2026-02-30") }
    rejected("invalid_timestamp") { PortalOtaProtocol.timestamp("2026-10-20T12:00:00-07:00") }
  }
  @Test fun corruptDownloadNeverReplacesActiveAndMissingAssetRecoversLastGood() {
    val dir = Files.createTempDirectory("portal-store").toFile()
    try {
      val store = PortalOtaStore(dir, keys, 69)
      store.activate(store.stage(pack(1)) { ByteArrayInputStream(image) }, now)
      rejected("asset_hash_mismatch") { store.stage(pack(2)) { ByteArrayInputStream(byteArrayOf(4,3,2,1)) } }
      assertEquals(1L, store.recover(now)?.revision)
      val two = store.stage(pack(2)) { ByteArrayInputStream(image) }; store.activate(two, now)
      assertTrue(dir.resolve("release-2/assets/${otaHash(image)}").delete())
      assertEquals(1L, PortalOtaStore(dir, keys, 69).recover(now)?.revision)
      rejected("old_revision") { store.stage(pack(2)) { ByteArrayInputStream(image) } }
    } finally { dir.deleteRecursively() }
  }
  @Test fun atomicPointerRecoversAndRetainsTwoPreviousGenerations() {
    val dir = Files.createTempDirectory("portal-store").toFile()
    try {
      val store = PortalOtaStore(dir, keys, 69)
      (1..4).forEach { store.activate(store.stage(pack(it)) { ByteArrayInputStream(image) }, now) }
      assertFalse(dir.resolve("release-1").exists())
      assertTrue(dir.resolve("release-2").isDirectory); assertTrue(dir.resolve("release-3").isDirectory)
      dir.resolve("active").writeText("torn")
      assertEquals(3L, PortalOtaStore(dir, keys, 69).recover(now)?.revision)
      assertEquals(4L, store.highestRevision)
    } finally { dir.deleteRecursively() }
  }
  @Test fun futureAndExpiredContentCannotActivateAndDatedEventsNeverRepeat() {
    val dir = Files.createTempDirectory("portal-store").toFile()
    try {
      val store = PortalOtaStore(dir, keys, 69)
      val future = PortalOtaProtocol.verify(envelope(fixture(activation = "2026-11-01T07:00:00Z")), keys, 69)
      val staged = store.stage(future) { ByteArrayInputStream(image) }
      rejected("not_yet_due") { store.activate(staged, now) }
      assertNull(store.recover(now)); assertNull(store.pending(now, 0))
      assertEquals(1L, store.pending(PortalOtaProtocol.timestamp("2026-11-01T08:00:00Z"), 0)?.revision)
      rejected("expired_package") { store.activate(staged, staged.validUntil) }
      assertNull(staged.events["2026-10-21"])
    } finally { dir.deleteRecursively() }
  }
  @Test fun providerCachePreservesFetchedAndObservedTimesSeparately() {
    val dir = Files.createTempDirectory("portal-provider").toFile()
    try {
      val body = "[{\"serverTimestamp\":1000,\"route\":{\"id\":\"J\"},\"stop\":{\"code\":\"18157\"},\"values\":[]}]"
      val cache = PortalOtaProviderCache(dir); cache.put("transit", body, now)
      val stored = cache.get("transit")!!
      assertEquals(now, stored.second)
      assertEquals(1000L, DailyDashboardData.parseTrains(stored.first).sourceAt)
      assertFalse(DailyDashboardData.trainFeedFresh(DailyDashboardData.parseTrains(stored.first), stored.second, now))
    } finally { dir.deleteRecursively() }
  }
}

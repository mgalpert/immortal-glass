/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.immortal.launcher.settings.SettingsDomains
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Private enrollment data never appears in logs, settings, health, acknowledgments, or errors. */
internal class PortalOtaClient(bootstrap: JSONObject, private val context: Context? = null) {
  private val endpoint = bootstrap.getString("endpoint").trimEnd('/')
  private var resolvedEndpoint = endpoint
  private val serviceName = bootstrap.optString("serviceName").takeIf { it.matches(Regex("FamilyPortal-[a-f0-9]{12}")) }
  private val serverName = bootstrap.optString("serverName").takeIf { it.matches(Regex("[A-Za-z0-9.-]+\\.local")) }
  private val token = bootstrap.getString("token")
  private val deviceId = bootstrap.getString("deviceId")
  val keys: Map<String, PublicKey>
  private val ssl: SSLContext
  init {
    val url = URL(endpoint)
    otaCheck(url.protocol == "https" && url.userInfo == null && url.query == null && url.ref == null && (url.path.isEmpty() || url.path == "/"), "invalid_bootstrap")
    otaCheck(token.isNotBlank() && token.length <= 4096 && !token.any { it == '\r' || it == '\n' }, "invalid_bootstrap")
    otaCheck(deviceId.matches(Regex("[A-Za-z0-9._-]{1,100}")), "invalid_bootstrap")
    val pin = bootstrap.getString("tlsCertificateSha256").lowercase()
    otaCheck(pin.matches(Regex("[a-f0-9]{64}")), "invalid_bootstrap")
    val keyJson = bootstrap.getJSONObject("signingKeys")
    otaCheck(keyJson.length() in 1..8, "invalid_bootstrap")
    keys = keyJson.keys().asSequence().associateWith { id ->
      otaCheck(id.matches(Regex("[A-Za-z0-9._-]{1,100}")), "invalid_bootstrap")
      PortalOtaProtocol.publicKey(keyJson.getString(id)).also { key ->
        otaCheck((key as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength()?.let { it >= 2048 } == true, "invalid_bootstrap")
      }
    }
    val trust = object : X509TrustManager {
      override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
      override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw java.security.cert.CertificateException("server_only") }
      override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty() || otaHash(chain[0].encoded) != pin) throw java.security.cert.CertificateException("pin_mismatch")
        chain[0].checkValidity()
      }
    }
    ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), java.security.SecureRandom()) }
  }
  private fun connection(path: String): HttpsURLConnection = (URL(resolvedEndpoint + path).openConnection() as HttpsURLConnection).apply {
    sslSocketFactory = ssl.socketFactory
    if (resolvedEndpoint != endpoint && serverName != null) {
      // Resolve the network address independently while retaining the enrolled DNS identity.
      hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session -> HttpsURLConnection.getDefaultHostnameVerifier().verify(serverName, session) }
    }
    // The platform hostname verifier is retained. This pin never alters global trust.
    instanceFollowRedirects = false; connectTimeout = 10_000; readTimeout = 20_000
    setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("X-Portal-Device", deviceId)
    setRequestProperty("Accept", "application/json")
  }
  fun release(): String? {
    return try { requestRelease() } catch (error: java.io.IOException) {
      val hint = if (context != null && serviceName != null && serverName != null) PortalOtaDiscovery.resolve(context, serviceName) else null
      if (hint == null) throw error
      resolvedEndpoint = hint
      requestRelease()
    }
  }
  private fun requestRelease(): String? {
    val conn = connection("/api/device/release")
    return try {
      if (conn.responseCode == 204) null else {
        otaCheck(conn.responseCode == 200, "release_http_error")
        conn.inputStream.use { bounded(it, PortalOtaProtocol.MAX_MANIFEST * 2).toString(Charsets.UTF_8) }
      }
    } finally { conn.disconnect() }
  }
  fun asset(asset: PortalAsset): InputStream {
    val conn = connection("/api/device/assets/${asset.hash}")
    try {
      otaCheck(conn.responseCode == 200, "asset_http_error")
      val length = conn.contentLengthLong
      otaCheck(length < 0 || length == asset.bytes, "asset_size_mismatch")
      return object : java.io.FilterInputStream(conn.inputStream) { override fun close() { try { super.close() } finally { conn.disconnect() } } }
    } catch (e: Exception) { conn.disconnect(); throw e }
  }
  fun ack(body: JSONObject) {
    val conn = connection("/api/device/ack")
    try {
      conn.requestMethod = "POST"; conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json")
      val data = body.toString().toByteArray(); conn.setFixedLengthStreamingMode(data.size)
      conn.outputStream.use { it.write(data) }; otaCheck(conn.responseCode in 200..299, "ack_http_error")
    } finally { conn.disconnect() }
  }
  companion object {
    fun bounded(input: InputStream, limit: Int): ByteArray {
      val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024)
      while (true) { val n = input.read(buffer); if (n < 0) break; otaCheck(output.size() + n <= limit, "response_too_large"); output.write(buffer, 0, n) }
      return output.toByteArray()
    }
  }
}

/** Cadence settings have one registry path; local changes increment the CAS version. */
object PortalOtaConfig {
  data class Settings(val enabled: Boolean = true, val pollSeconds: Int = 60, val localVersion: Long = 0)
  private fun prefs(c: Context) = c.getSharedPreferences("portal_ota", Context.MODE_PRIVATE)
  fun load(c: Context) = prefs(c).run { Settings(getBoolean("enabled", true), getInt("pollSeconds", 60), getLong("localVersion", 0)) }
  fun setEnabled(c: Context, value: Boolean) { prefs(c).edit().putBoolean("enabled", value).apply() }
  fun setPollSeconds(c: Context, value: Int) { prefs(c).edit().putInt("pollSeconds", value).apply() }
  fun changed(c: Context) { val p = prefs(c); p.edit().putLong("localVersion", p.getLong("localVersion", 0) + 1).commit(); PortalOta.refresh() }
}

/** One low-priority worker. Never starts an Activity, touches alarms, installs APKs, or takes focus. */
internal object PortalOta {
  var current by mutableStateOf<PortalPackage?>(null); private set
  private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "portal-content").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
  private val main = Handler(Looper.getMainLooper())
  private var app: Application? = null
  private var client: PortalOtaClient? = null
  private var store: PortalOtaStore? = null
  private var lastAttempt = 0L
  private var nextAttempt = 0L
  private var failures = 0
  private val holds = AtomicInteger(0)
  @Volatile private var healthJson = JSONObject().put("status", "not_enrolled").toString()
  private var downloadedAt: String? = null
  private var appliedAt: String? = null
  private var lastSyncAt: String? = null
  private var settingsConflict = false
  private var appliedRevision = 0L
  private var appliedKeyId: String? = null
  private var stateLoaded = false
  fun holdLesson() { holds.incrementAndGet() }
  fun releaseLesson() { holds.updateAndGet { (it - 1).coerceAtLeast(0) }; refresh() }
  fun health(): JSONObject = JSONObject(healthJson)
  fun refresh() { if (app != null) worker.execute { tick(true) } }
  fun start(application: Application) {
    if (app != null) return
    app = application
    // Do this synchronously before HomeActivity/Fleet can query the upstream updater.
    // Our signed native capabilities travel through the family's separate release channel.
    if (File(application.filesDir, "portal-bootstrap.json").isFile ||
        application.getExternalFilesDir(null)?.resolve("portal-bootstrap.json")?.isFile == true) {
      DevMode.setEnabled(application, true)
    }
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityStarted(activity: Activity) { refresh() }
      override fun onActivityCreated(activity: Activity, state: Bundle?) {}
      override fun onActivityResumed(activity: Activity) { refresh() }
      override fun onActivityPaused(activity: Activity) {}
      override fun onActivityStopped(activity: Activity) {}
      override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
      override fun onActivityDestroyed(activity: Activity) {}
    })
    worker.scheduleWithFixedDelay({ runCatching { tick(false) } }, 0, 60, TimeUnit.SECONDS)
  }
  private fun enroll(c: Context) {
    val credentialFile = File(c.filesDir, "portal-bootstrap.json")
    val external = c.getExternalFilesDir(null)?.resolve("portal-bootstrap.json")
    // Validate the complete proposed enrollment before replacing the private credential file.
    if (external?.isFile == true) {
      otaCheck(external.length() <= 64 * 1024, "invalid_bootstrap")
      val raw = external.readText(); PortalOtaClient(JSONObject(raw), c)
      DevMode.setEnabled(c, true)
      PortalOtaStore.atomicWrite(credentialFile, raw); otaCheck(external.delete(), "bootstrap_cleanup_failed")
      client = null; store = null
      failures = 0; nextAttempt = 0; lastAttempt = 0
    }
    if (client == null && credentialFile.isFile) {
      otaCheck(credentialFile.length() <= 64 * 1024, "invalid_bootstrap")
      client = PortalOtaClient(JSONObject(credentialFile.readText()), c)
      val version = c.packageManager.getPackageInfo(c.packageName, 0).versionCode.toLong()
      store = PortalOtaStore(File(c.filesDir, "portal-content"), client!!.keys, version)
      val recovered = store!!.recover(System.currentTimeMillis())
      if (recovered != null) adopt(recovered)
    }
  }
  private fun nowIso(at: Long = System.currentTimeMillis()): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(at))
  private fun adopt(pack: PortalPackage) {
    val c = app ?: return
    if (holds.get() > 0 || TimerStore.load(c).ringing) return
    val otaPrefs = c.getSharedPreferences("portal_ota_state", Context.MODE_PRIVATE)
    appliedAt = if (otaPrefs.getLong("appliedRevision", 0) == pack.revision) otaPrefs.getString("appliedAt", null) ?: nowIso() else nowIso()
    appliedRevision = pack.revision
    appliedKeyId = JSONObject(pack.envelope).getString("keyId")
    otaPrefs.edit().putLong("appliedRevision", appliedRevision).putString("appliedAt", appliedAt).commit()
    main.post { current = pack }
    if (otaPrefs.getLong("settingsAppliedRevision", 0) != pack.revision) {
      settingsConflict = false
      synchronized(SettingsDomains.portal) {
        val desired = pack.desiredSettings
        val values = desired.getJSONObject("values")
        if (values.length() > 0) {
          if (desired.getLong("baseLocalVersion") != PortalOtaConfig.load(c).localVersion) {
            settingsConflict = true
          } else SettingsDomains.portal.apply(c, values)
        }
      }
      otaPrefs.edit().putLong("settingsAppliedRevision", pack.revision).putBoolean("settingsConflict", settingsConflict).commit()
    } else settingsConflict = otaPrefs.getBoolean("settingsConflict", false)
    updateHealth(if (settingsConflict) "settings_conflict" else "applied", if (settingsConflict) "settings_conflict" else null)
  }
  private fun tick(force: Boolean) {
    val c = app ?: return
    val now = System.currentTimeMillis()
    try {
      if (!stateLoaded) {
        val p = c.getSharedPreferences("portal_ota_state", Context.MODE_PRIVATE)
        downloadedAt = p.getString("downloadedAt", null); appliedAt = p.getString("appliedAt", null); lastSyncAt = p.getString("lastSyncAt", null)
        stateLoaded = true
      }
      enroll(c)
      val s = store ?: return
      val cl = client ?: return
      // Locally staged future releases become due even while the Mac or network is unavailable.
      val safeToAdopt = holds.get() == 0 && !TimerStore.load(c).ringing
      if (safeToAdopt) s.pending(now, appliedRevision)?.let { adopt(s.activate(it, now)) }
      if (current?.validUntil?.let { now >= it } == true) { main.post { current = null }; appliedRevision = 0 }
      val settings = PortalOtaConfig.load(c)
      if (!settings.enabled) {
        updateHealth("disabled")
        if (now >= nextAttempt && now - lastAttempt >= settings.pollSeconds * 1000L) { lastAttempt = now; cl.ack(health()) }
        return
      }
      if (now < nextAttempt || (!force && now - lastAttempt < settings.pollSeconds * 1000L)) return
      // Foreground catchup can bypass cadence but never hammer a failed endpoint.
      if (force && now - lastAttempt < 15_000) return
      lastAttempt = now
      val envelope = cl.release()
      if (envelope != null) {
        val version = c.packageManager.getPackageInfo(c.packageName, 0).versionCode.toLong()
        val pack = PortalOtaProtocol.verify(envelope, cl.keys, version)
        otaCheck(now < pack.validUntil, "expired_package")
        if (pack.revision > s.highestRevision) {
          val staged = s.stage(pack, cl::asset); downloadedAt = nowIso()
          val activationNow = System.currentTimeMillis()
          if (activationNow >= pack.activateAt && holds.get() == 0 && !TimerStore.load(c).ringing) adopt(s.activate(staged, activationNow))
        } else otaCheck(pack.revision == s.highestRevision, "old_revision")
      }
      lastSyncAt = nowIso(); failures = 0; nextAttempt = 0
      c.getSharedPreferences("portal_ota_state", Context.MODE_PRIVATE).edit().putString("downloadedAt", downloadedAt).putString("lastSyncAt", lastSyncAt).commit()
      val state = if (s.highestRevision > appliedRevision) "staged" else if (appliedRevision > 0) "applied" else "bundled"
      updateHealth(if (settingsConflict) "settings_conflict" else state, if (settingsConflict) "settings_conflict" else null)
      cl.ack(health())
    } catch (e: Exception) {
      failures++; nextAttempt = now + (30_000L * (1L shl minOf(failures - 1, 7))).coerceAtMost(60 * 60_000L)
      val error = if (e is PortalOtaError) e.code else when (e) {
        is javax.net.ssl.SSLException -> "tls_error"
        is java.io.IOException -> "network_error"
        else -> "invalid_response"
      }
      updateHealth("error", error)
      runCatching { client?.ack(health()) }
    }
  }
  private fun updateHealth(status: String, error: String? = null) {
    healthJson = JSONObject().put("revision", appliedRevision).put("downloadedAt", downloadedAt ?: JSONObject.NULL)
        .put("appliedAt", appliedAt ?: JSONObject.NULL).put("lastSyncAt", lastSyncAt ?: JSONObject.NULL)
        .put("trustedKeyIds", org.json.JSONArray(client?.keys?.keys?.toList().orEmpty()))
        .put("signingKeyId", appliedKeyId ?: JSONObject.NULL)
        .put("status", status).put("localDate", PortalOtaProtocol.localDay(System.currentTimeMillis()))
        .put("localSettingsVersion", app?.let { PortalOtaConfig.load(it).localVersion } ?: 0L)
        .put("settingsConflict", settingsConflict).apply { if (error != null) put("error", error) }.toString()
  }
}

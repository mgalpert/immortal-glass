/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey

/** Immutable, self-contained generations; only the tiny active pointer changes during activation. */
internal class PortalOtaStore(private val root: File, private val keys: Map<String, PublicKey>, private val appVersion: Long) {
  init { root.mkdirs() }
  val highestRevision: Long get() = root.resolve("highest").takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: generations().firstOrNull()?.name?.removePrefix("release-")?.toLongOrNull() ?: 0L
  private fun directory(revision: Long) = root.resolve("release-$revision")
  private fun read(dir: File): PortalPackage {
    val envelope = dir.resolve("envelope.json")
    otaCheck(envelope.isFile && envelope.length() <= PortalOtaProtocol.MAX_MANIFEST * 2, "incomplete_package")
    val pack = PortalOtaProtocol.verify(envelope.readText(), keys, appVersion)
    otaCheck(dir.name == "release-${pack.revision}", "invalid_package")
    pack.assets.forEach { a -> otaCheck(validAsset(dir.resolve("assets/${a.hash}"), a), "corrupt_asset") }
    return pack.copy(directory = dir)
  }
  @Synchronized fun stage(pack: PortalPackage, fetch: (PortalAsset) -> InputStream): PortalPackage {
    val target = directory(pack.revision)
    if (target.isDirectory) {
      val present = runCatching { read(target) }.getOrNull()
      if (present != null) { otaCheck(present.envelope == pack.envelope, "revision_collision"); return present }
    }
    otaCheck(pack.revision > highestRevision, "old_revision")
    otaCheck(root.usableSpace >= pack.assets.distinctBy { it.hash }.sumOf { it.bytes } + 4L * 1024 * 1024, "storage_full")
    val stage = root.resolve("staging")
    // This directory is owned solely by the OTA worker; never touches photo or app storage.
    if (stage.exists()) stage.deleteRecursively()
    otaCheck(stage.mkdirs(), "storage_error")
    try {
      val assets = stage.resolve("assets"); otaCheck(assets.mkdir(), "storage_error")
      pack.assets.distinctBy { it.hash }.forEach { a ->
        val file = assets.resolve(a.hash)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        fetch(a).use { input -> FileOutputStream(file).use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) { val count = input.read(buffer); if (count < 0) break
            size += count; otaCheck(size <= a.bytes && size <= PortalOtaProtocol.MAX_ASSET, "asset_size_mismatch")
            digest.update(buffer, 0, count); output.write(buffer, 0, count)
          }
          output.fd.sync()
        } }
        otaCheck(size == a.bytes && digest.digest().joinToString("") { "%02x".format(it) } == a.hash, "asset_hash_mismatch")
      }
      atomicWrite(stage.resolve("envelope.json"), pack.envelope)
      if (target.exists()) target.deleteRecursively()
      otaCheck(stage.renameTo(target), "storage_error")
      atomicWrite(root.resolve("highest"), pack.revision.toString())
      val active = root.resolve("active").takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
      prune(active)
      return read(target)
    } catch (e: Exception) { stage.deleteRecursively(); throw e }
  }
  @Synchronized fun activate(pack: PortalPackage, now: Long): PortalPackage {
    otaCheck(now >= pack.activateAt, "not_yet_due"); otaCheck(now < pack.validUntil, "expired_package")
    val complete = read(directory(pack.revision))
    val old = root.resolve("active").takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
    if (old != null && old != pack.revision) atomicWrite(root.resolve("last-good"), old.toString())
    atomicWrite(root.resolve("active"), pack.revision.toString())
    prune(pack.revision)
    return complete
  }
  /** A torn pointer, failed write, corrupt asset, or deleted generation cannot strand the UI. */
  @Synchronized fun recover(now: Long): PortalPackage? {
    val requested = listOf("active", "last-good").mapNotNull { root.resolve(it).takeIf { f -> f.isFile }?.readText()?.trim()?.toLongOrNull() }
    val revisions = (requested + generations().map { it.name.removePrefix("release-").toLong() }).distinct()
    revisions.forEach { revision ->
      val pack = runCatching { read(directory(revision)) }.getOrNull()
      if (pack != null && now >= pack.activateAt && now < pack.validUntil) {
        atomicWrite(root.resolve("active"), revision.toString()); return pack
      }
    }
    return null
  }
  @Synchronized fun pending(now: Long, activeRevision: Long): PortalPackage? = generations().asSequence()
      .filter { it.name.removePrefix("release-").toLong() > activeRevision }
      .mapNotNull { dir -> runCatching {
        val envelope = dir.resolve("envelope.json")
        otaCheck(envelope.isFile && envelope.length() <= PortalOtaProtocol.MAX_MANIFEST * 2)
        val pack = PortalOtaProtocol.verify(envelope.readText(), keys, appVersion)
        if (now >= pack.activateAt && now < pack.validUntil) read(dir) else null
      }.getOrNull() }.maxByOrNull { it.revision }
  private fun generations(): List<File> = root.listFiles().orEmpty().filter { it.isDirectory && Regex("release-[1-9][0-9]*").matches(it.name) }
      .sortedByDescending { it.name.removePrefix("release-").toLongOrNull() ?: 0 }
  private fun prune(activeRevision: Long) {
    val all = generations()
    val previous = all.filter { it.name.removePrefix("release-").toLong() < activeRevision }
        .filter { runCatching { read(it) }.isSuccess }.take(2)
    val keep = (previous.map { it.name } + "release-$activeRevision" + all.filter { it.name.removePrefix("release-").toLong() > activeRevision }.take(1).map { it.name }).toSet()
    all.filter { it.name !in keep }.forEach { it.deleteRecursively() }
  }
  companion object {
    fun validAsset(file: File, a: PortalAsset): Boolean {
      if (!file.isFile || file.length() != a.bytes) return false
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
      return digest.digest().joinToString("") { "%02x".format(it) } == a.hash
    }
    fun atomicWrite(file: File, value: String) {
      val temp = File(file.parentFile, file.name + ".tmp")
      FileOutputStream(temp).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
      otaCheck(temp.renameTo(file), "storage_error")
    }
  }
}

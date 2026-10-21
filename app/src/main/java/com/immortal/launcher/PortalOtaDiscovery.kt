/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.immortal.launcher

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Bonjour is only an address hint. The enrolled certificate pin still authenticates every byte. */
internal object PortalOtaDiscovery {
  fun resolve(context: Context, name: String): String? {
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
    val result = AtomicReference<String?>(null)
    val resolving = AtomicBoolean(false)
    val finished = AtomicBoolean(false)
    val latch = CountDownLatch(1)
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val multicast = wifi?.createMulticastLock("portal-publisher-discovery")?.apply { setReferenceCounted(false) }
    val listener = object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(type: String) {}
      override fun onDiscoveryStopped(type: String) {}
      override fun onServiceLost(service: NsdServiceInfo) {}
      override fun onStopDiscoveryFailed(type: String, error: Int) {}
      override fun onStartDiscoveryFailed(type: String, error: Int) { latch.countDown() }
      override fun onServiceFound(service: NsdServiceInfo) {
        if (finished.get() || service.serviceName != name || !resolving.compareAndSet(false, true)) return
        runCatching { manager.resolveService(service, object : NsdManager.ResolveListener {
          override fun onResolveFailed(service: NsdServiceInfo, error: Int) { latch.countDown() }
          override fun onServiceResolved(service: NsdServiceInfo) {
            val host = service.host
            if (!finished.get() && host is Inet4Address && host.isSiteLocalAddress && service.port == 9443) {
              result.set("https://${host.hostAddress}:9443")
            }
            latch.countDown()
          }
        }) }.onFailure { latch.countDown() }
      }
    }
    return try {
      multicast?.acquire()
      manager.discoverServices("_contains-portal._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
      latch.await(8, TimeUnit.SECONDS)
      result.get()
    } catch (_: Exception) { null }
    finally {
      finished.set(true)
      runCatching { manager.stopServiceDiscovery(listener) }
      if (multicast?.isHeld == true) runCatching { multicast.release() }
    }
  }
}

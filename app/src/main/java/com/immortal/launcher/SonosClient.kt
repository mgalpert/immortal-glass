/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.immortal.launcher

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

data class SonosRoom(val id: String, val name: String, val host: String)
data class SonosStatus(val room: SonosRoom, val coordinator: SonosRoom, val playing: Boolean,
    val title: String, val artist: String, val artUrl: String?, val volume: Int,
    val canNext: Boolean, val canPrevious: Boolean)
data class SonosFavorite(val id: String, val title: String, val uri: String,
    val metadata: String, val container: Boolean)

/** Blocking LAN-only UPnP client. Call from an IO dispatcher; mutations are never retried. */
class SonosClient(
    private val soap: SoapTransport = SoapTransport { room, service, action, args ->
        http(URL("http://${validatedHost(room.host)}:1400/${servicePath(service)}/Control"), service, action, args)
    },
    private val search: (InetAddress?) -> List<Pair<String, String>> = ::ssdp,
    private val description: (String) -> String = { http(URL(it)) }
) {
    fun interface SoapTransport {
        fun call(room: SonosRoom, service: String, action: String, arguments: Map<String, String>): String
    }

    fun discover(bindAddress: InetAddress? = null): List<SonosRoom> {
        val rooms = search(bindAddress).distinctBy { it.first }.take(32).mapNotNull { (host, location) ->
            runCatching {
                val url = localUrl(location, host)
                val device = xml(description(url.toString()))
                require(device.text("manufacturer").contains("Sonos", ignoreCase = true))
                require(device.text("deviceType").contains(":ZonePlayer:"))
                SonosRoom(device.text("UDN").removePrefix("uuid:").also { require(it.isNotBlank()) },
                    device.text("roomName").ifBlank { device.text("friendlyName") }.ifBlank { "Sonos" }, host)
            }.getOrNull()
        }.distinctBy { it.id }
        if (rooms.isEmpty()) return emptyList()
        // Topology hides the secondary channel of stereo pairs and supplies all visible rooms.
        return runCatching { topology(rooms.first()).flatMap { it.second }.distinctBy { it.id } }
            .getOrElse { rooms }.sortedBy { it.name.lowercase() }
    }

    fun status(room: SonosRoom): SonosStatus {
        val coordinator = coordinator(room)
        val state = call(coordinator, AV, "GetTransportInfo", instance()).text("CurrentTransportState")
        val position = call(coordinator, AV, "GetPositionInfo", instance())
        val raw = position.text("TrackMetaData")
        val metadata = if (raw.isBlank() || raw == "NOT_IMPLEMENTED") null else xml(raw)
        val title = metadata?.text("title").orEmpty().ifBlank { "Nothing playing" }
        val artist = metadata?.text("creator").orEmpty().ifBlank { metadata?.text("artist").orEmpty() }
        val art = metadata?.text("albumArtURI").orEmpty().takeIf { it.isNotBlank() }?.let {
            runCatching { URL(URL("http://${validatedHost(coordinator.host)}:1400"), it) }
                .getOrNull()?.takeIf { u -> u.protocol in setOf("http", "https") && u.userInfo == null }?.toString()
        }
        val volume = call(room, RC, "GetVolume", instance() + ("Channel" to "Master"))
            .text("CurrentVolume").toIntOrNull()?.takeIf { it in 0..100 }
            ?: throw IOException("Sonos returned an invalid volume")
        val actions = runCatching { call(coordinator, AV, "GetCurrentTransportActions", instance())
            .text("Actions").split(',').map { it.trim() } }.getOrDefault(emptyList())
        return SonosStatus(room, coordinator, state == "PLAYING", title, artist, art, volume,
            "Next" in actions, "Previous" in actions)
    }

    fun favorites(room: SonosRoom): List<SonosFavorite> {
        val result = mutableListOf<SonosFavorite>()
        var index = 0
        repeat(10) {
            val page = call(room, CD, "Browse", mapOf("ObjectID" to "FV:2", "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*", "StartingIndex" to index.toString(), "RequestedCount" to "100", "SortCriteria" to ""))
            val raw = page.text("Result")
            if (raw.isNotBlank()) result += parseFavorites(raw)
            val count = page.text("NumberReturned").toIntOrNull() ?: return result.distinctBy { it.id }
            index += count
            if (count == 0 || index >= (page.text("TotalMatches").toIntOrNull() ?: index))
                return result.distinctBy { it.id }
        }
        return result.distinctBy { it.id }
    }

    fun transport(room: SonosRoom, action: String) {
        require(action in setOf("Play", "Pause", "Next", "Previous")) { "Unsupported playback action" }
        call(coordinator(room), AV, action, instance() + if (action == "Play") mapOf("Speed" to "1") else emptyMap())
    }

    fun setVolume(room: SonosRoom, volume: Int) {
        require(volume in 0..100) { "Volume must be between 0 and 100" }
        call(room, RC, "SetVolume", instance() + mapOf("Channel" to "Master", "DesiredVolume" to volume.toString()))
    }

    fun playFavorite(room: SonosRoom, favorite: SonosFavorite) {
        require(favorite.uri.isNotBlank()) { "This favorite cannot be played" }
        val target = coordinator(room)
        val queue = favorite.container || favorite.uri.startsWith("x-sonos-spotify:") ||
            favorite.uri.startsWith("x-rincon-cpcontainer:")
        if (queue) {
            val added = call(target, AV, "AddURIToQueue", instance() + mapOf("EnqueuedURI" to favorite.uri,
                "EnqueuedURIMetaData" to favorite.metadata, "DesiredFirstTrackNumberEnqueued" to "0", "EnqueueAsNext" to "0"))
            val first = added.text("FirstTrackNumberEnqueued").toIntOrNull()?.takeIf { it > 0 }
                ?: throw IOException("Sonos could not add this favorite to the queue")
            call(target, AV, "SetAVTransportURI", instance() + mapOf("CurrentURI" to "x-rincon-queue:${target.id}#0", "CurrentURIMetaData" to ""))
            call(target, AV, "Seek", instance() + mapOf("Unit" to "TRACK_NR", "Target" to first.toString()))
        } else {
            call(target, AV, "SetAVTransportURI", instance() + mapOf("CurrentURI" to favorite.uri, "CurrentURIMetaData" to favorite.metadata))
        }
        call(target, AV, "Play", instance() + ("Speed" to "1"))
    }

    private fun coordinator(room: SonosRoom): SonosRoom = topology(room).firstOrNull { (_, members) ->
        members.any { it.id == room.id }
    }?.first ?: throw IOException("Sonos room is unavailable; refresh rooms")

    private fun topology(room: SonosRoom): List<Pair<SonosRoom, List<SonosRoom>>> {
        val state = xml(call(room, ZG, "GetZoneGroupState", emptyMap()).text("ZoneGroupState"))
        return state.elements("ZoneGroup").mapNotNull { group ->
            val members = group.elements("ZoneGroupMember").filter { it.getAttribute("Invisible") != "1" }.mapNotNull { member ->
                runCatching {
                    val url = localUrl(member.getAttribute("Location"))
                    SonosRoom(member.getAttribute("UUID"), member.getAttribute("ZoneName"), url.host)
                }.getOrNull()
            }
            members.find { it.id == group.getAttribute("Coordinator") }?.let { it to members }
        }
    }

    private fun call(room: SonosRoom, service: String, action: String, args: Map<String, String>): Element {
        validatedHost(room.host)
        val result = xml(soap.call(room, service, action, args))
        if (result.elements("Fault").isNotEmpty()) throw IOException(faultMessage(result.text("errorCode")))
        return result
    }

    companion object {
        private const val AV = "AVTransport"
        private const val RC = "RenderingControl"
        private const val CD = "ContentDirectory"
        private const val ZG = "ZoneGroupTopology"
        private const val LIMIT = 2 * 1024 * 1024
        private fun instance() = mapOf("InstanceID" to "0")
        private fun servicePath(service: String) = when (service) {
            AV, RC -> "MediaRenderer/$service"
            CD -> "MediaServer/$service"
            ZG -> "ZoneGroupTopology"
            else -> throw IllegalArgumentException("Unknown Sonos service")
        }
        internal fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        internal fun envelope(service: String, action: String, args: Map<String, String>): String =
            """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:$service:1">""" +
                args.entries.joinToString("") { (key, value) -> "<$key>${escape(value)}</$key>" } + "</u:$action></s:Body></s:Envelope>"

        internal fun parseFavorites(raw: String): List<SonosFavorite> = xml(raw).elements("item").mapNotNull { item ->
            val uri = item.text("res")
            if (uri.isBlank()) return@mapNotNull null // Navigational shortcuts are not playable.
            val metadata = item.text("resMD")
            val innerClass = if (metadata.isBlank()) "" else xml(metadata).text("class")
            SonosFavorite(item.getAttribute("id"), item.text("title"), uri, metadata, innerClass.startsWith("object.container"))
        }

        internal fun xml(raw: String): Element {
            require(raw.toByteArray(Charsets.UTF_8).size <= LIMIT) { "Sonos response is too large" }
            require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(raw)) { "Unsafe Sonos XML" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // Android's parser does not implement every JAXP feature. The declaration guard
                // and rejecting resolver also apply when a feature is unavailable.
                for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities"))
                    runCatching { setFeature(feature, false) }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
                setErrorHandler(DefaultHandler())
            }
            return builder.parse(InputSource(StringReader(raw))).documentElement
        }
        private fun Node.elements(name: String): List<Element> {
            val list = (this as Element).getElementsByTagNameNS("*", name)
            return (0 until list.length).map { list.item(it) as Element }
        }
        private fun Element.text(name: String) = elements(name).firstOrNull()?.textContent.orEmpty()

        internal fun validatedHost(host: String): String {
            val parts = host.split('.').map { it.toIntOrNull() ?: -1 }
            require(parts.size == 4 && parts.all { it in 0..255 } &&
                (parts[0] == 10 || parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1] in 16..31)) { "Sonos requires a private IPv4 address" }
            return parts.joinToString(".")
        }
        internal fun localUrl(location: String, expectedHost: String? = null): URL {
            val url = URL(location)
            require(url.protocol == "http" && url.userInfo == null && url.ref == null &&
                (url.port == -1 || url.port == 1400)) { "Invalid Sonos address" }
            val host = validatedHost(url.host)
            require(expectedHost == null || host == expectedHost) { "Sonos discovery address mismatch" }
            return URL("http", host, 1400, url.file)
        }

        private fun http(url: URL, service: String? = null, action: String? = null, args: Map<String, String> = emptyMap()): String {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.instanceFollowRedirects = false
                if (service != null && action != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    val body = envelope(service, action, args).toByteArray(Charsets.UTF_8)
                    // Streaming prevents HttpURLConnection from buffering/replaying a mutation.
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.setRequestProperty("Connection", "close")
                    connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                    connection.setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > LIMIT) throw IOException("Sonos response is too large")
                        output.write(buffer, 0, read)
                    }
                    output.toString("UTF-8")
                }.orEmpty()
                if (status !in 200..299) {
                    val code = runCatching { xml(raw).text("errorCode") }.getOrDefault("")
                    throw IOException(faultMessage(code))
                }
                return raw
            } catch (_: SocketTimeoutException) {
                throw IOException("Sonos did not respond; check the room connection")
            } finally { connection.disconnect() }
        }
        private fun faultMessage(code: String) = when (code) {
            "701", "711" -> "This playback action is unavailable right now"
            "714", "800", "801" -> "Sonos could not play this favorite; check Spotify in the Sonos app"
            else -> "Sonos could not complete this action" + if (code.matches(Regex("[0-9]{3}"))) " ($code)" else ""
        }

        private fun ssdp(bindAddress: InetAddress?): List<Pair<String, String>> {
            val found = linkedSetOf<Pair<String, String>>()
            DatagramSocket(null).use { socket ->
                socket.bind(InetSocketAddress(bindAddress, 0))
                val query = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n".toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName("239.255.255.250"), 1900))
                val deadline = System.nanoTime() + 3_000_000_000L
                while (found.size < 32) {
                    val remaining = (deadline - System.nanoTime()) / 1_000_000L
                    if (remaining <= 0) break
                    socket.soTimeout = remaining.toInt().coerceAtLeast(1)
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { break }
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (!response.startsWith("HTTP/1.1 200")) continue
                    val location = response.lineSequence().firstOrNull { it.startsWith("LOCATION:", true) }?.substringAfter(':')?.trim() ?: continue
                    val host = packet.address.hostAddress ?: continue
                    if (runCatching { localUrl(location, host) }.isSuccess) found += host to location
                }
            }
            return found.toList()
        }
    }
}

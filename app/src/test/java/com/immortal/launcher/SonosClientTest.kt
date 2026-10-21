/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.immortal.launcher

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SonosClientTest {
    private val selected = SonosRoom("RINCON_SELECTED", "Kitchen", "192.168.1.10")
    private val coordinator = SonosRoom("RINCON_COORD", "Family Room", "192.168.1.11")
    private data class Request(val room: SonosRoom, val service: String, val action: String, val args: Map<String, String>)
    private val requests = mutableListOf<Request>()
    private val topology = """<ZoneGroupState><ZoneGroups><ZoneGroup Coordinator="RINCON_COORD"><ZoneGroupMember UUID="RINCON_SELECTED" ZoneName="Kitchen" Location="http://192.168.1.10:1400/xml/device_description.xml"/><ZoneGroupMember UUID="RINCON_COORD" ZoneName="Family Room" Location="http://192.168.1.11:1400/xml/device_description.xml"/><ZoneGroupMember UUID="HIDDEN" ZoneName="Stereo right" Invisible="1" Location="http://192.168.1.12:1400/xml/device_description.xml"/></ZoneGroup></ZoneGroups></ZoneGroupState>"""
    private fun response(vararg fields: Pair<String, String>) = "<Response>" + fields.joinToString("") { (k, v) -> "<$k>${SonosClient.escape(v)}</$k>" } + "</Response>"
    private fun client(handler: (Request) -> String = { "<Response/>" }) = SonosClient(soap = SonosClient.SoapTransport { room, service, action, args ->
        val request = Request(room, service, action, args)
        requests += request
        if (action == "GetZoneGroupState") response("ZoneGroupState" to topology) else handler(request)
    })
    private fun metadata(kind: String = "object.container.playlistContainer") = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="playlist"><dc:title>Try &amp; Try</dc:title><dc:creator>A &amp; B</dc:creator><upnp:class>$kind</upnp:class><upnp:albumArtURI>/getaa?s=1&amp;u=2</upnp:albumArtURI></item></DIDL-Lite>"""

    @Test fun statusReadsCoordinatorPlaybackButSelectedRoomVolumeAndDecodesMetadata() {
        val client = client { when (it.action) {
            "GetTransportInfo" -> response("CurrentTransportState" to "PLAYING")
            "GetPositionInfo" -> response("TrackMetaData" to metadata())
            "GetVolume" -> response("CurrentVolume" to "27")
            "GetCurrentTransportActions" -> response("Actions" to "Pause, Next, Previous")
            else -> error("Unexpected action")
        } }
        val state = client.status(selected)
        assertEquals(selected, state.room)
        assertEquals(coordinator, state.coordinator)
        assertEquals("Try & Try", state.title)
        assertEquals("A & B", state.artist)
        assertEquals("http://192.168.1.11:1400/getaa?s=1&u=2", state.artUrl)
        assertEquals(27, state.volume)
        assertTrue(state.playing)
        assertTrue(state.canNext)
        assertTrue(state.canPrevious)
        assertEquals(selected, requests.single { it.action == "GetVolume" }.room)
        assertTrue(requests.filter { it.action in setOf("GetTransportInfo", "GetPositionInfo", "GetCurrentTransportActions") }.all { it.room == coordinator })
    }

    @Test fun unimplementedTrackMetadataProducesFriendlyIdleState() {
        val client = client { when (it.action) {
            "GetTransportInfo" -> response("CurrentTransportState" to "STOPPED")
            "GetPositionInfo" -> response("TrackMetaData" to "NOT_IMPLEMENTED")
            "GetVolume" -> response("CurrentVolume" to "0")
            "GetCurrentTransportActions" -> throw IOException("unsupported")
            else -> error("Unexpected action")
        } }
        val state = client.status(selected)
        assertEquals("Nothing playing", state.title)
        assertFalse(state.playing)
        assertFalse(state.canNext)
        assertFalse(state.canPrevious)
        assertNull(state.artUrl)
    }

    @Test fun nextOnlyActionsDoNotEnablePrevious() {
        val client = client { when (it.action) {
            "GetTransportInfo" -> response("CurrentTransportState" to "PLAYING")
            "GetPositionInfo" -> response("TrackMetaData" to metadata())
            "GetVolume" -> response("CurrentVolume" to "27")
            "GetCurrentTransportActions" -> response("Actions" to "Pause, Next")
            else -> error("Unexpected action")
        } }
        val state = client.status(selected)
        assertTrue(state.canNext)
        assertFalse(state.canPrevious)
    }

    @Test fun previousOnlyActionsDoNotEnableNext() {
        val client = client { when (it.action) {
            "GetTransportInfo" -> response("CurrentTransportState" to "PLAYING")
            "GetPositionInfo" -> response("TrackMetaData" to metadata())
            "GetVolume" -> response("CurrentVolume" to "27")
            "GetCurrentTransportActions" -> response("Actions" to "Pause, Previous")
            else -> error("Unexpected action")
        } }
        val state = client.status(selected)
        assertFalse(state.canNext)
        assertTrue(state.canPrevious)
    }

    @Test fun transportResolvesCoordinatorEachTimeAndWhitelistsActionsBeforeSending() {
        val client = client()
        client.transport(selected, "Play")
        client.transport(selected, "Pause")
        assertEquals(2, requests.count { it.action == "GetZoneGroupState" })
        assertEquals(coordinator, requests.first { it.action == "Play" }.room)
        assertEquals("1", requests.first { it.action == "Play" }.args["Speed"])
        assertFalse(requests.first { it.action == "Pause" }.args.containsKey("Speed"))
        val count = requests.size
        assertThrows(IllegalArgumentException::class.java) { client.transport(selected, "RemoveAllTracksFromQueue") }
        assertEquals(count, requests.size)
    }

    @Test fun volumeStaysOnSelectedSpeakerAndRejectsOutOfRangeValues() {
        val client = client()
        client.setVolume(selected, 100)
        assertEquals(selected, requests.single().room)
        assertEquals("RenderingControl", requests.single().service)
        assertEquals(mapOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to "100"), requests.single().args)
        assertThrows(IllegalArgumentException::class.java) { client.setVolume(selected, -1) }
        assertThrows(IllegalArgumentException::class.java) { client.setVolume(selected, 101) }
        assertEquals(1, requests.size)
    }

    @Test fun favoriteBrowsePreservesEmbeddedMetadataAndSkipsNavigationShortcuts() {
        val meta = metadata()
        val didl = """<DIDL-Lite xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="FV:2/7"><dc:title>Sleep &amp; songs</dc:title><res>x-rincon-cpcontainer:spotify%3aplaylist%3aabc?sid=9&amp;sn=1</res><r:resMD>${SonosClient.escape(meta)}</r:resMD></item><item id="FV:2/8"><dc:title>Browse only</dc:title></item></DIDL-Lite>"""
        val client = client { response("Result" to didl, "NumberReturned" to "2", "TotalMatches" to "2") }
        val favorite = client.favorites(selected).single()
        assertEquals("FV:2/7", favorite.id)
        assertEquals("Sleep & songs", favorite.title)
        assertEquals("x-rincon-cpcontainer:spotify%3aplaylist%3aabc?sid=9&sn=1", favorite.uri)
        assertEquals(meta, favorite.metadata)
        assertTrue(favorite.container)
        assertEquals("FV:2", requests.single().args["ObjectID"])
    }

    @Test fun spotifyFavoriteAppendsWithoutClearingThenSeeksReturnedQueuePosition() {
        val client = client { if (it.action == "AddURIToQueue") response("FirstTrackNumberEnqueued" to "42") else "<Response/>" }
        val favorite = SonosFavorite("f", "Playlist", "x-rincon-cpcontainer:spotify", metadata(), true)
        client.playFavorite(selected, favorite)
        assertEquals(listOf("GetZoneGroupState", "AddURIToQueue", "SetAVTransportURI", "Seek", "Play"), requests.map { it.action })
        val add = requests[1]
        assertEquals(favorite.uri, add.args["EnqueuedURI"])
        assertEquals(favorite.metadata, add.args["EnqueuedURIMetaData"])
        assertEquals("0", add.args["DesiredFirstTrackNumberEnqueued"])
        assertEquals("0", add.args["EnqueueAsNext"])
        assertEquals("x-rincon-queue:RINCON_COORD#0", requests[2].args["CurrentURI"])
        assertEquals(mapOf("InstanceID" to "0", "Unit" to "TRACK_NR", "Target" to "42"), requests[3].args)
        assertTrue(requests.drop(1).all { it.room == coordinator })
    }

    @Test fun spotifyTrackAlsoUsesQueueEvenWhenNotContainer() {
        val client = client { if (it.action == "AddURIToQueue") response("FirstTrackNumberEnqueued" to "1") else "<Response/>" }
        client.playFavorite(selected, SonosFavorite("f", "Track", "x-sonos-spotify:spotify%3atrack%3aabc", metadata("object.item.audioItem.musicTrack"), false))
        assertTrue(requests.any { it.action == "AddURIToQueue" })
    }

    @Test fun radioFavoriteSetsUriDirectlyWithoutChangingQueue() {
        val client = client()
        val favorite = SonosFavorite("radio", "Radio", "x-sonosapi-stream:s1?sid=254&flags=32", "radio metadata", false)
        client.playFavorite(selected, favorite)
        assertEquals(listOf("GetZoneGroupState", "SetAVTransportURI", "Play"), requests.map { it.action })
        assertEquals(favorite.uri, requests[1].args["CurrentURI"])
        assertEquals(favorite.metadata, requests[1].args["CurrentURIMetaData"])
    }

    @Test fun rejectedQueueAddStopsWithoutRetryOrChangingCurrentTransport() {
        val client = client { "<Response/>" }
        assertThrows(IOException::class.java) { client.playFavorite(selected, SonosFavorite("f", "Playlist", "x-rincon-cpcontainer:a", metadata(), true)) }
        assertEquals(listOf("GetZoneGroupState", "AddURIToQueue"), requests.map { it.action })
    }

    @Test fun soapFaultBecomesFriendlyErrorAndDoesNotExposeRemoteDetails() {
        val client = client { "<Envelope><Fault><detail><UPnPError><errorCode>714</errorCode><errorDescription>secret-token-uri</errorDescription></UPnPError></detail></Fault></Envelope>" }
        val error = assertThrows(IOException::class.java) { client.transport(selected, "Play") }
        assertTrue(error.message!!.contains("Sonos app"))
        assertFalse(error.message!!.contains("secret"))
        assertEquals(1, requests.count { it.action == "Play" })
    }

    @Test fun soapEnvelopeEscapesUriAndMetadataExactlyOnce() {
        val value = "x:a?one=1&two=<\"'>"
        val xml = SonosClient.envelope("AVTransport", "SetAVTransportURI", mapOf("CurrentURI" to value))
        assertTrue(xml.contains("x:a?one=1&amp;two=&lt;&quot;&apos;&gt;"))
        assertEquals(value, SonosClient.xml(xml).getElementsByTagName("CurrentURI").item(0).textContent)
    }

    @Test fun xmlRejectsDoctypeEntitiesAndOversizeBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { SonosClient.xml("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>") }
        assertThrows(IllegalArgumentException::class.java) { SonosClient.xml("<!ENTITY e 'hello'><x/>") }
        assertThrows(IllegalArgumentException::class.java) { SonosClient.xml("<x>" + "a".repeat(2 * 1024 * 1024) + "</x>") }
    }

    @Test fun discoveryValidatesSenderDescriptionAndHidesStereoMembers() {
        val device = "<root><device><deviceType>urn:schemas-upnp-org:device:ZonePlayer:1</deviceType><manufacturer>Sonos, Inc.</manufacturer><UDN>uuid:RINCON_SELECTED</UDN><roomName>Kitchen</roomName></device></root>"
        var descriptions = 0
        val client = SonosClient(soap = SonosClient.SoapTransport { _, _, _, _ -> response("ZoneGroupState" to topology) },
            search = { listOf("192.168.1.10" to "http://192.168.1.10:1400/xml/device_description.xml", "192.168.1.20" to "http://192.168.1.99:1400/evil") },
            description = { descriptions++; device })
        assertEquals(listOf(coordinator, selected), client.discover())
        assertEquals(1, descriptions)
    }

    @Test fun discoveryRejectsNonSonosAndNonPrivateOrRedirectAddresses() {
        for (location in listOf("http://8.8.8.8:1400/a", "http://127.0.0.1:1400/a", "https://192.168.1.10:1400/a", "http://user:pass@192.168.1.10:1400/a", "http://192.168.1.10:80/a"))
            assertThrows(IllegalArgumentException::class.java) { SonosClient.localUrl(location) }
        val client = SonosClient(search = { listOf("192.168.1.10" to "http://192.168.1.10:1400/a") },
            description = { "<root><manufacturer>Other</manufacturer><deviceType>ZonePlayer</deviceType></root>" })
        assertTrue(client.discover().isEmpty())
        assertEquals("http://10.0.0.2:1400/a", SonosClient.localUrl("http://10.0.0.2/a").toString())
    }
}

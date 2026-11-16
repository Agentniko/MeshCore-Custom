package io.github.thatsfguy.meshcore.custom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCardTest {
    @Test fun roundTrip() {
        val c = ProfileCard("Niko", "niko#1", "123456789012345678", "https://discord.com/users/123456789012345678", "hi; there=ok")
        val text = assertNotNull(ProfileCardCodec.encode(c))
        assertTrue(ProfileCardCodec.byteSize(text) <= 160)
        assertEquals(c, ProfileCardCodec.decode(text))
    }
    @Test fun startsWithHarmlessText() {
        assertTrue(ProfileCardCodec.encode(ProfileCard("A"))!!.startsWith("MCC1;n=A"))
    }
    @Test fun dropsDescriptionThenLinkWhenTooLong() {
        val c = ProfileCard("N".repeat(32), "d".repeat(40), null, "https://discord.com/" + "a".repeat(60), "x".repeat(60))
        val text = assertNotNull(ProfileCardCodec.encode(c))
        assertTrue(ProfileCardCodec.byteSize(text) <= 160)
        assertEquals("N".repeat(32), ProfileCardCodec.decode(text)!!.name)
    }
    @Test fun rejectsUnsafeLinkAndBadId() {
        val d = ProfileCardCodec.decode("MCC1;n=A;l=https%3A//evil.example/x;i=12")!!
        assertNull(d.discordLink); assertNull(d.discordId)
        assertNull(ProfileCardCodec.sanitize(ProfileCard("A", discordLink = "javascript:alert(1)"))!!.discordLink)
    }
    @Test fun rejectsMalformed() {
        assertNull(ProfileCardCodec.decode("hello"))
        assertNull(ProfileCardCodec.decode("MCC2;n=A"))
        assertNull(ProfileCardCodec.decode("MCC1;d=x"))
        assertNull(ProfileCardCodec.decode("MCC1;n=A;n=B"))
        assertNull(ProfileCardCodec.decode("MCC1;n=%ZZ"))
        assertNull(ProfileCardCodec.decode("MCC1;n=%4"))
        assertNull(ProfileCardCodec.decode("MCC1;n=" + "a".repeat(200)))
    }
    @Test fun stripsControlCharsAndIgnoresUnknownKeys() {
        val d = ProfileCardCodec.decode("MCC1;n=Bo%00b;zz=1")!!
        assertEquals("Bob", d.name)
    }
    @Test fun noNameNoCard() { assertNull(ProfileCardCodec.encode(ProfileCard("   "))) }
}

class ThemeSpecTest {
    private fun sample() = ThemeSpec(
        "My \"theme\"", true,
        ThemeSpec.COLOR_KEYS.mapIndexed { i, k -> k to (0x101010 * (i + 1) and 0xFFFFFF) }.toMap(),
        cardRadius = 16, panelOpacity = 0.9,
    )
    @Test fun roundTrip() {
        val t = sample()
        val back = ThemeSpecCodec.decode(ThemeSpecCodec.encode(t))
        assertEquals(ThemeImport.Ok(t), back)
    }
    @Test fun rejectsBadInput() {
        fun bad(s: String) = assertTrue(ThemeSpecCodec.decode(s) is ThemeImport.Rejected, s)
        bad("not json"); bad("{}"); bad("[1]"); bad("{\"a\":{\"b\":1}}")
        bad("{\"format\":\"x\",\"version\":1}")
        bad(ThemeSpecCodec.encode(sample()).replace("\"#", "\"red#"))
        bad(ThemeSpecCodec.encode(sample()).replace("\"version\": 1", "\"version\": 9"))
        bad(ThemeSpecCodec.encode(sample()) + " junk")
        bad(ThemeSpecCodec.encode(sample()).replace("\"primary\"", "\"primary\": \"#000000\", \"primary\""))
        bad("{" + "\"k\":1,".repeat(70) + "\"z\":1}")
    }
    @Test fun clampsNumbers() {
        val j = ThemeSpecCodec.encode(sample()).replace("\"cardRadius\": 16", "\"cardRadius\": 9999")
            .replace("\"fontScale\": 1.0", "\"fontScale\": 0.01")
        val ok = ThemeSpecCodec.decode(j) as ThemeImport.Ok
        assertEquals(32, ok.theme.cardRadius); assertEquals(0.8, ok.theme.fontScale)
    }
    @Test fun contrast() {
        assertTrue(abs(ThemeSpecCodec.contrast(0x000000, 0xFFFFFF) - 21.0) < 0.01)
        assertEquals("#0A0B0C", ThemeSpecCodec.hex(0x0A0B0C))
    }
}

class RadioLinkTest {
    private val p = RadioParams()
    private fun flat(km: Double, n: Int = 40, elev: Double? = 100.0) =
        (0 until n).map { TerrainPoint(km * 1000 * it / (n - 1), elev) }

    @Test fun sensitivityMatchesLoraRule() {
        // SF11 / 250 kHz, NF 6 dB: -174 + 54 + 6 - 17.5 = -131.5
        assertTrue(abs(RadioLink.sensitivityDbm(p) - (-131.5)) < 0.1)
        assertTrue(abs(RadioLink.sensitivityDbm(RadioParams(spreadingFactor = 12, bandwidthKHz = 125.0)) - (-137.0)) < 0.1)
    }
    @Test fun freeSpaceKnownValue() {
        // 1 km @ 868 MHz ≈ 91.2 dB
        assertTrue(abs(RadioLink.freeSpaceLossDb(1.0, 868.0) - 91.2) < 0.2)
    }
    @Test fun flatShortLinkIsGood() {
        val r = RadioLink.analyse(flat(2.0), p.copy(txHeightM = 10.0, rxHeightM = 10.0))
        assertEquals(true, r.lineOfSight); assertEquals(Quality.GOOD, r.quality); assertEquals(Confidence.MEDIUM, r.confidence)
    }
    @Test fun ridgeBlocksAndAddsLoss() {
        val ridge = flat(10.0).mapIndexed { i, t -> if (i == 20) t.copy(elevationM = 400.0) else t }
        val r = RadioLink.analyse(ridge, p)
        assertEquals(false, r.lineOfSight)
        assertTrue(r.diffractionLossDb > 15.0, "loss=${r.diffractionLossDb}")
        assertTrue(r.marginDb < RadioLink.analyse(flat(10.0), p).marginDb)
    }
    @Test fun earthBulgeBlocksVeryLongFlatPath() {
        assertEquals(false, RadioLink.analyse(flat(80.0, 80), p).lineOfSight)
    }
    @Test fun missingEndpointIsUnknown() {
        val prof = flat(5.0).toMutableList().also { it[0] = TerrainPoint(0.0, null) }
        val r = RadioLink.analyse(prof, p)
        assertEquals(Quality.UNKNOWN, r.quality); assertEquals(Confidence.NONE, r.confidence)
    }
    @Test fun missingSamplesLowerConfidence() {
        val prof = flat(5.0).mapIndexed { i, t -> if (i == 10) t.copy(elevationM = null) else t }
        val r = RadioLink.analyse(prof, p)
        assertEquals(Confidence.LOW, r.confidence); assertEquals(1, r.missingSamples)
    }
    @Test fun destinationAndDistanceAgree() {
        val (la, lo) = RadioLink.destination(48.0, 2.0, 90.0, 10_000.0)
        assertTrue(abs(RadioLink.distanceM(48.0, 2.0, la, lo) - 10_000.0) < 5.0)
    }
    @Test fun coverageShrinksBehindHillAndCancels() {
        val hill = { la: Double, lo: Double -> if (lo > 2.03 && lo < 2.04) 500.0 else 100.0 }
        val cells = CoverageModel.compute(48.0, 2.0, 8000.0, 8, 16, p.copy(txHeightM = 5.0), hill)
        assertTrue(cells.isNotEmpty())
        val east = cells.filter { it.lon > 2.05 }
        val west = cells.filter { it.lon < 1.95 }
        assertTrue(east.isNotEmpty() && west.isNotEmpty())
        assertTrue(east.minOf { it.marginDb!! } < west.minOf { it.marginDb!! })
        assertTrue(CoverageModel.compute(48.0, 2.0, 8000.0, 8, 16, p, hill, cancelled = { true }).isEmpty())
    }
}

class DiscordAvatarUrlTest {
    private val id = "123456789012345678"
    @Test fun acceptsAndNormalises() {
        assertEquals("https://cdn.discordapp.com/avatars/$id/abc123.png?size=256",
            DiscordAvatarUrl.normalise("https://cdn.discordapp.com/avatars/$id/abc123.webp?size=1024"))
        assertEquals("https://cdn.discordapp.com/avatars/$id/a_f00.png?size=256",
            DiscordAvatarUrl.normalise("  https://cdn.discordapp.com/avatars/$id/a_f00.gif  "))
    }
    @Test fun rejectsOtherHostsAndShapes() {
        for (bad in listOf(
            "http://cdn.discordapp.com/avatars/$id/abc.png",
            "https://evil.example/avatars/$id/abc.png",
            "https://cdn.discordapp.com.evil.example/avatars/$id/abc.png",
            "https://cdn.discordapp.com@evil.example/avatars/$id/abc.png",
            "https://cdn.discordapp.com/avatars/12/abc.png",
            "https://cdn.discordapp.com/avatars/$id/../x.png",
            "https://cdn.discordapp.com/avatars/$id/abc.svg",
            "https://cdn.discordapp.com/avatars/$id/abc/def.png",
            "https://cdn.discordapp.com/attachments/$id/abc.png",
            "",
        )) assertNull(DiscordAvatarUrl.normalise(bad), bad)
    }
}

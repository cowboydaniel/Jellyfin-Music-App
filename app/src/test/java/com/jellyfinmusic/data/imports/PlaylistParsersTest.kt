package com.jellyfinmusic.data.imports

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistParsersTest {

    @Test
    fun `m3u uses EXTINF metadata when present`() {
        val content = """
            #EXTM3U
            #EXTINF:245,Timmy Trumpet - Oracle
            /music/Timmy Trumpet/Oracle.flac
            #EXTINF:180,Patty Gurdy - The Longing
            /music/Patty Gurdy/The Longing.mp3
        """.trimIndent()

        val tracks = PlaylistParsers.parseM3u(content)

        assertEquals(2, tracks.size)
        assertEquals("Timmy Trumpet", tracks[0].artist)
        assertEquals("Oracle", tracks[0].title)
        assertEquals("/music/Timmy Trumpet/Oracle.flac", tracks[0].path)
    }

    @Test
    fun `m3u falls back to the file name when EXTINF is absent`() {
        val content = """
            #EXTM3U
            /music/Alesso/Heroes_We_Could_Be.mp3
        """.trimIndent()

        val tracks = PlaylistParsers.parseM3u(content)

        assertEquals(1, tracks.size)
        assertEquals("Heroes We Could Be", tracks[0].title)
    }

    @Test
    fun `csv reads exportify style headers in any column order`() {
        val content = """
            "Track Name","Artist Name","Album Name"
            "Oracle","Timmy Trumpet","Oracle"
            "Tsunami, Radio Edit","DVBBS","Tsunami"
        """.trimIndent()

        val tracks = PlaylistParsers.parseCsv(content)

        assertEquals(2, tracks.size)
        assertEquals("Oracle", tracks[0].title)
        assertEquals("Timmy Trumpet", tracks[0].artist)
        // The quoted comma must not split the field.
        assertEquals("Tsunami, Radio Edit", tracks[1].title)
    }

    @Test
    fun `text splits on the common separators`() {
        val tracks = PlaylistParsers.parseText(
            """
            Alesso - Heroes
            Patty Gurdy – The Longing
            Just A Title
            """.trimIndent()
        )

        assertEquals(3, tracks.size)
        assertEquals("Alesso" to "Heroes", tracks[0].artist to tracks[0].title)
        assertEquals("Patty Gurdy" to "The Longing", tracks[1].artist to tracks[1].title)
        assertEquals("" to "Just A Title", tracks[2].artist to tracks[2].title)
    }

    @Test
    fun `normalize strips production noise so exported titles match library tags`() {
        assertEquals("oracle", "Oracle (Official Music Video)".normalize())
        assertEquals("heroes we could be", "Heroes (we could be) [HD]".normalize())
        assertEquals("the longing", "The Longing (Official Video)".normalize())
        assertEquals("closer", "Closer feat. Halsey".normalize())
        assertEquals("dont stop", "Don't Stop!".normalize())
        // Curly apostrophes are what most exports actually contain.
        assertEquals("dont stop", "Don\u2019t Stop!".normalize())
        assertEquals("dont stop", "Dont Stop".normalize())
    }
}

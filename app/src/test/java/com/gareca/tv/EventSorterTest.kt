package com.gareca.tv

import com.gareca.tv.model.EventItem
import com.gareca.tv.model.EventStatus
import com.gareca.tv.model.StreamOption
import com.gareca.tv.util.EventSorter
import com.gareca.tv.util.GlobalSortMode
import com.gareca.tv.util.ReplaySortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSorterTest {

    private fun createDummyItem(
        title: String,
        status: EventStatus = EventStatus.SCHEDULED,
        startEpochMillis: Long = 0L,
        time: String? = null,
        category: String = "Partidos",
        country: String? = null,
        timestamp: Long = 0L
    ): EventItem {
        return EventItem(
            title = title,
            time = time,
            subtitle = "Sub $title",
            streams = listOf(StreamOption("Opción 1", "https://example.com/live.m3u8", "exo")),
            status = status,
            category = category,
            country = country,
            startEpochMillis = startEpochMillis,
            timestamp = timestamp
        )
    }

    @Test
    fun testSortDailyMatchesChronologically() {
        val match1 = createDummyItem("Match Early", startEpochMillis = 1000L, time = "10:00")
        val match2 = createDummyItem("Match Late", startEpochMillis = 5000L, time = "18:00")
        val match3 = createDummyItem("Match Middle", startEpochMillis = 3000L, time = "14:00")
        val matchNoEpoch = createDummyItem("Match No Epoch", startEpochMillis = 0L, time = "20:00")

        val sorted = EventSorter.sortDailyMatchesChronologically(listOf(match2, matchNoEpoch, match1, match3))

        assertEquals("Match Early should be first", "Match Early", sorted[0].title)
        assertEquals("Match Middle should be second", "Match Middle", sorted[1].title)
        assertEquals("Match Late should be third", "Match Late", sorted[2].title)
        assertEquals("Match without startEpochMillis should be last", "Match No Epoch", sorted[3].title)
    }

    @Test
    fun testSortEventListStatusPriorities() {
        val live = createDummyItem("Item Live", status = EventStatus.LIVE)
        val soon = createDummyItem("Item Soon", status = EventStatus.STARTING_SOON)
        val scheduled = createDummyItem("Item Scheduled", status = EventStatus.SCHEDULED)
        val channel247 = createDummyItem("Item 247", status = EventStatus.CHANNEL_24_7)
        val replay = createDummyItem("Item Replay", status = EventStatus.REPLAY)
        val unavailable = createDummyItem("Item Unavailable", status = EventStatus.REPLAY_UNAVAILABLE)
        val finished = createDummyItem("Item Finished", status = EventStatus.FINISHED)

        val unsorted = listOf(finished, scheduled, unavailable, live, channel247, soon, replay)
        val sorted = EventSorter.sortEventList(unsorted)

        assertEquals("LIVE has highest priority", "Item Live", sorted[0].title)
        assertEquals("STARTING_SOON has 2nd priority", "Item Soon", sorted[1].title)
        assertEquals("SCHEDULED has 3rd priority", "Item Scheduled", sorted[2].title)
        assertEquals("CHANNEL_24_7 has 4th priority", "Item 247", sorted[3].title)
        assertEquals("REPLAY has 5th priority", "Item Replay", sorted[4].title)
        assertEquals("REPLAY_UNAVAILABLE goes near end", "Item Unavailable", sorted[5].title)
        assertEquals("FINISHED always goes strictly last", "Item Finished", sorted[6].title)
    }

    @Test
    fun testSortCxtvChannelListPutsUnavailableLastAndSortsAlphabetically() {
        val ch1 = createDummyItem("CNN", category = "Noticias", country = "EEUU", status = EventStatus.CHANNEL_24_7)
        val ch2 = createDummyItem("BBC", category = "Noticias", country = "UK", status = EventStatus.CHANNEL_24_7)
        val ch3 = createDummyItem("HBO", category = "Cine", country = "EEUU", status = EventStatus.CHANNEL_24_7)
        val chDown = createDummyItem("Canal Caído", category = "Cine", country = "Argentina", status = EventStatus.REPLAY_UNAVAILABLE)

        val sorted = EventSorter.sortCxtvChannelList(listOf(chDown, ch1, ch2, ch3))

        // Cine comes before Noticias alphabetically
        assertEquals("First should be HBO (Cine)", "HBO", sorted[0].title)
        // In Noticias: EEUU comes before UK
        assertEquals("Second should be CNN (EEUU)", "CNN", sorted[1].title)
        assertEquals("Third should be BBC (UK)", "BBC", sorted[2].title)
        assertEquals("Unavailable channel must be at the very bottom", "Canal Caído", sorted[3].title)
    }

    @Test
    fun testSortReplayEventsByDateAndName() {
        val r1 = createDummyItem("Boca vs River", timestamp = 100L)
        val r2 = createDummyItem("Racing vs Independiente", timestamp = 300L)
        val r3 = createDummyItem("Alianza vs Cristal", timestamp = 200L)

        val byDateDesc = EventSorter.sortReplayEvents(listOf(r1, r2, r3), ReplaySortMode.DATE_DESC)
        assertEquals("Highest timestamp first in DATE_DESC", "Racing vs Independiente", byDateDesc[0].title)
        assertEquals("Lowest timestamp last in DATE_DESC", "Boca vs River", byDateDesc[2].title)

        val byNameAsc = EventSorter.sortReplayEvents(listOf(r1, r2, r3), ReplaySortMode.NAME_ASC)
        assertEquals("A-Z first", "Alianza vs Cristal", byNameAsc[0].title)
        assertEquals("A-Z second", "Boca vs River", byNameAsc[1].title)
        assertEquals("A-Z third", "Racing vs Independiente", byNameAsc[2].title)
    }

    @Test
    fun testSortGlobalSearchEvents() {
        val c1 = createDummyItem("Zebra Sports")
        val c2 = createDummyItem("Alpha Sports")

        val sortedAsc = EventSorter.sortGlobalSearchEvents(listOf(c1, c2), GlobalSortMode.NAME_ASC)
        assertEquals("Alpha Sports should come first in NAME_ASC", "Alpha Sports", sortedAsc[0].title)

        val sortedDesc = EventSorter.sortGlobalSearchEvents(listOf(c1, c2), GlobalSortMode.NAME_DESC)
        assertEquals("Zebra Sports should come first in NAME_DESC", "Zebra Sports", sortedDesc[0].title)
    }
}

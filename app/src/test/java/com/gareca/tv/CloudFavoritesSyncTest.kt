package com.gareca.tv

import com.gareca.tv.auth.CloudFavoriteItem
import com.gareca.tv.auth.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFavoritesSyncTest {

    @Test
    fun testCloudFavoriteItemModel() {
        val item = CloudFavoriteItem(
            email = "usuario@garecatv.com",
            channelKey = "tyc sports",
            source = "sports"
        )
        assertEquals("usuario@garecatv.com", item.email)
        assertEquals("tyc sports", item.channelKey)
        assertEquals("sports", item.source)
        assertTrue("updatedAt should be positive timestamp", item.updatedAt > 0L)
    }

    @Test
    fun testSyncResultSealedClass() {
        val success: SyncResult = SyncResult.Success(pulled = 3, pushed = 2)
        assertTrue(success is SyncResult.Success)
        assertEquals(3, (success as SyncResult.Success).pulled)
        assertEquals(2, success.pushed)

        val error: SyncResult = SyncResult.Error("Network offline")
        assertTrue(error is SyncResult.Error)
        assertEquals("Network offline", (error as SyncResult.Error).message)

        val notLogged: SyncResult = SyncResult.NotLoggedIn
        assertTrue(notLogged is SyncResult.NotLoggedIn)
    }
}

package com.ebuddy.android

import com.ebuddy.android.data.remote.SyncResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test fun cursorIsParsedAsLong(){assertEquals(42L,SyncResponse(emptyList(),"42",false).nextCursor.toLong())}
    @Test fun emptyPageDoesNotAdvanceCursor(){val old=7L;val page=SyncResponse(emptyList(),"7",false);assertEquals(old,page.nextCursor.toLong())}
}

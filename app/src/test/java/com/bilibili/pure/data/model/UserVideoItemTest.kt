package com.bilibili.pure.data.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class UserVideoItemTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(UserVideoItem::class.java, UserVideoItem.deserializer)
        .create()

    private fun parse(json: String): UserVideoItem =
        gson.fromJson(JsonParser().parse(json), UserVideoItem::class.java)

    @Test
    fun parsesCreatedAsPubdateAndLengthAsDuration() {
        val item = parse(
            """
            {
                "bvid": "BV1ac5yzhE94",
                "aid": 114375683741573,
                "title": "和朋友去西藏拍样片日记",
                "pic": "http://i0.hdslb.com/bfs/archive/x.jpg",
                "play": 224185,
                "video_review": 2365,
                "created": 1745290800,
                "length": "22:11",
                "description": "desc",
                "mid": 946974,
                "author": "影视飓风"
            }
            """.trimIndent()
        )
        assertEquals(1745290800L, item.pubdate)
        assertEquals("22:11", item.duration)
        assertEquals(224185L, item.playCount)
        assertEquals(2365L, item.danmakuCount)
        assertEquals("BV1ac5yzhE94", item.bvid)
        assertEquals("影视飓风", item.author)
    }

    @Test
    fun fallsBackToPubdateKeyWhenCreatedMissing() {
        val item = parse("""{"bvid": "BV1xx", "pubdate": 1575621902}""")
        assertEquals(1575621902L, item.pubdate)
    }

    @Test
    fun missingCreatedAndLengthYieldsZeroAndBlank() {
        val item = parse("""{"bvid": "BV1yy", "title": "t"}""")
        assertEquals(0L, item.pubdate)
        assertEquals("", item.duration)
        assertEquals(0L, item.playCount)
    }
}

package com.bilibili.pure.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackEmailTest {

    @Test
    fun `blank content is rejected`() {
        assertFalse(FeedbackEmail.isValid(""))
        assertFalse(FeedbackEmail.isValid("   "))
        assertFalse(FeedbackEmail.isValid("\n\t "))
    }

    @Test
    fun `non-blank content is accepted`() {
        assertTrue(FeedbackEmail.isValid("播放器闪退"))
        assertTrue(FeedbackEmail.isValid("  有空格的内容  "))
    }

    @Test
    fun `bug subject contains type label and version`() {
        val subject = FeedbackEmail.subject(FeedbackType.BUG, "1.6.3")
        assertEquals("[Bug反馈] bilibili-pure v1.6.3", subject)
    }

    @Test
    fun `suggestion subject contains type label and version`() {
        val subject = FeedbackEmail.subject(FeedbackType.SUGGESTION, "1.6.3")
        assertEquals("[功能建议] bilibili-pure v1.6.3", subject)
    }

    @Test
    fun `body contains content and environment info`() {
        val body = FeedbackEmail.body(
            content = "进入播放页黑屏",
            contact = "",
            version = "1.6.3",
            androidVersion = "14",
            deviceModel = "24117RK2CC"
        )
        assertTrue(body.contains("进入播放页黑屏"))
        assertTrue(body.contains("1.6.3"))
        assertTrue(body.contains("14"))
        assertTrue(body.contains("24117RK2CC"))
    }

    @Test
    fun `body omits contact line when contact blank`() {
        val body = FeedbackEmail.body(
            content = "内容",
            contact = "   ",
            version = "1.6.3",
            androidVersion = "14",
            deviceModel = "Pixel"
        )
        assertFalse(body.contains("联系方式"))
    }

    @Test
    fun `body includes contact line when contact provided`() {
        val body = FeedbackEmail.body(
            content = "内容",
            contact = "qq123456",
            version = "1.6.3",
            androidVersion = "14",
            deviceModel = "Pixel"
        )
        assertTrue(body.contains("联系方式"))
        assertTrue(body.contains("qq123456"))
    }

    @Test
    fun `mailto uri targets recipient`() {
        assertEquals("mailto:1017714519@qq.com", FeedbackEmail.mailtoUri())
    }
}

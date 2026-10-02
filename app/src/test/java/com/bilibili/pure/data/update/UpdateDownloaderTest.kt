package com.bilibili.pure.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun apkFileName_keeps_safe_tag() {
        assertEquals(
            "bilibili-pure-v1.7.21.apk",
            UpdateDownloader.apkFileName("v1.7.21")
        )
    }

    @Test
    fun apkFileName_sanitizes_unsafe_characters() {
        val name = UpdateDownloader.apkFileName("v1/7 21*")
        assertEquals("bilibili-pure-v1_7_21_.apk", name)
        assertTrue(name.matches(Regex("[A-Za-z0-9._-]+")))
    }

    @Test
    fun isValidApk_false_when_file_missing() {
        val missing = File(tmp.root, "nope.apk")
        assertFalse(UpdateDownloader.isValidApk(missing, 100L))
    }

    @Test
    fun isValidApk_false_when_empty_file() {
        val f = tmp.newFile("empty.apk")
        assertFalse(UpdateDownloader.isValidApk(f, 100L))
    }

    @Test
    fun isValidApk_false_when_size_mismatch() {
        val f = tmp.newFile("partial.apk").apply { writeBytes(ByteArray(50)) }
        assertFalse(UpdateDownloader.isValidApk(f, 100L))
    }

    @Test
    fun isValidApk_false_when_expected_size_invalid() {
        val f = tmp.newFile("any.apk").apply { writeBytes(ByteArray(50)) }
        assertFalse(UpdateDownloader.isValidApk(f, 0L))
        assertFalse(UpdateDownloader.isValidApk(f, -1L))
    }

    @Test
    fun isValidApk_true_when_size_matches() {
        val f = tmp.newFile("good.apk").apply { writeBytes(ByteArray(100)) }
        assertTrue(UpdateDownloader.isValidApk(f, 100L))
    }

    @Test
    fun matchesRelease_true_when_package_and_version_match() {
        assertTrue(
            UpdateDownloader.matchesRelease(
                "com.bilibili.pure", "1.7.20", "com.bilibili.pure", "v1.7.20"
            )
        )
    }

    @Test
    fun matchesRelease_true_when_tag_has_no_v_prefix() {
        assertTrue(
            UpdateDownloader.matchesRelease(
                "com.bilibili.pure", "1.7.20", "com.bilibili.pure", "1.7.20"
            )
        )
    }

    @Test
    fun matchesRelease_false_when_package_differs() {
        assertFalse(
            UpdateDownloader.matchesRelease(
                "com.other.app", "1.7.20", "com.bilibili.pure", "v1.7.20"
            )
        )
    }

    @Test
    fun matchesRelease_false_when_version_differs() {
        assertFalse(
            UpdateDownloader.matchesRelease(
                "com.bilibili.pure", "1.7.19", "com.bilibili.pure", "v1.7.20"
            )
        )
    }

    @Test
    fun matchesRelease_false_when_metadata_missing() {
        assertFalse(
            UpdateDownloader.matchesRelease(null, null, "com.bilibili.pure", "v1.7.20")
        )
        assertFalse(
            UpdateDownloader.matchesRelease("com.bilibili.pure", null, "com.bilibili.pure", "v1.7.20")
        )
    }
}

package com.bilibili.pure.util

import com.bilibili.pure.data.model.SubtitleCue

object SubtitleParser {

    fun findCurrentCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        val positionSec = positionMs / 1000.0

        var low = 0
        var high = cues.size - 1

        while (low <= high) {
            val mid = (low + high) / 2
            val cue = cues[mid]

            when {
                positionSec < cue.from -> high = mid - 1
                positionSec > cue.to -> low = mid + 1
                else -> return cue
            }
        }

        return null
    }

    fun findCurrentCues(cues: List<SubtitleCue>, positionMs: Long): List<SubtitleCue> {
        if (cues.isEmpty()) return emptyList()
        val positionSec = positionMs / 1000.0
        return cues.filter { cue ->
            positionSec >= cue.from && positionSec <= cue.to
        }
    }
}

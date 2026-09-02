package com.bilibili.pure.ui.player

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.bilibili.pure.R
import com.bilibili.pure.data.model.QualityOption
import com.bilibili.pure.data.model.SubtitleTrack
import androidx.media3.ui.PlayerView

class PlayerControlBar(
    private val onQualitySelected: (QualityOption) -> Unit,
    private val onSubtitleSelected: (SubtitleTrack?) -> Unit
) {
    private var speedCtl: SpeedController? = null

    private var speedButton: TextView? = null
    private var qualityButton: TextView? = null
    private var subtitleButton: TextView? = null

    private var currentSpeed = 1.0f
    private var currentQuality: QualityOption? = null
    private var availableQualities: List<QualityOption> = emptyList()
    private var availableSubtitles: List<SubtitleTrack> = emptyList()
    private var currentSubtitle: SubtitleTrack? = null
    private var subtitleEnabled: Boolean = false

    fun attach(ctx: Context, playerView: PlayerView, speedCtl: SpeedController) {
        this.speedCtl = speedCtl

        val settingsId = ctx.resources.getIdentifier("exo_settings", "id", ctx.packageName)
        if (settingsId == 0) return

        val settingsView = playerView.findViewById<View>(settingsId)
        val row = settingsView?.parent as? ViewGroup ?: return

        if (subtitleButton == null) {
            subtitleButton = createSubtitleButton(ctx).also { row.addView(it, 0) }
        }
        if (speedButton == null) {
            speedButton = createSpeedButton(ctx).also { row.addView(it, 0) }
        }
        if (qualityButton == null) {
            qualityButton = createQualityButton(ctx).also { row.addView(it, 0) }
        }
    }

    fun update(
        newSpeed: Float,
        newQuality: QualityOption?,
        newQualities: List<QualityOption>,
        newSubtitles: List<SubtitleTrack>,
        newCurrentSubtitle: SubtitleTrack?,
        newSubtitleEnabled: Boolean
    ) {
        currentSpeed = newSpeed
        currentQuality = newQuality
        availableQualities = newQualities
        availableSubtitles = newSubtitles
        currentSubtitle = newCurrentSubtitle
        subtitleEnabled = newSubtitleEnabled

        speedButton?.text = "%.1fx".format(currentSpeed)

        val qb = qualityButton ?: return
        qb.visibility = if (newQualities.size > 1) View.VISIBLE else View.GONE
        qb.text = currentQuality?.description ?: "画质"

        val sb = subtitleButton ?: return
        if (newSubtitles.isEmpty()) {
            sb.visibility = View.GONE
        } else {
            sb.visibility = View.VISIBLE
            sb.text = when {
                !newSubtitleEnabled -> "CC"
                newCurrentSubtitle?.isAiSubtitle == true -> "AI"
                newCurrentSubtitle != null -> newCurrentSubtitle.lanDoc.take(2)
                else -> "CC"
            }
        }
    }

    private fun createSubtitleButton(ctx: Context): TextView {
        val density = ctx.resources.displayMetrics.density
        val btnSize = (48 * density).toInt()
        val btnMargin = (2 * density).toInt()

        return TextView(ctx).apply {
            text = "CC"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).also {
                it.leftMargin = btnMargin
                it.rightMargin = btnMargin
            }
            setOnClickListener { anchor ->
                val subtitles = availableSubtitles
                if (subtitles.isEmpty()) return@setOnClickListener
                val darkCtx = ContextThemeWrapper(ctx, R.style.ThemeOverlay_Bilibili_DarkPopup)
                val popup = PopupMenu(darkCtx, anchor, Gravity.TOP)

                popup.menu.add(0, -1, 0, "关闭字幕").apply {
                    isChecked = !subtitleEnabled
                }
                subtitles.forEachIndexed { i, track ->
                    popup.menu.add(0, i, 0, track.displayName).apply {
                        isChecked = track.id == currentSubtitle?.id && subtitleEnabled
                    }
                }

                popup.menu.setGroupCheckable(0, true, false)

                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == -1) {
                        onSubtitleSelected(null)
                    } else {
                        val track = subtitles.getOrNull(item.itemId)
                        if (track != null) {
                            onSubtitleSelected(track)
                        }
                    }
                    true
                }
                popup.show()
            }
        }
    }

    private fun createSpeedButton(ctx: Context): TextView {
        val density = ctx.resources.displayMetrics.density
        val btnSize = (48 * density).toInt()
        val btnMargin = (2 * density).toInt()

        return TextView(ctx).apply {
            text = "1.0x"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).also {
                it.leftMargin = btnMargin
                it.rightMargin = btnMargin
            }
            setOnClickListener { anchor ->
                val ctl = speedCtl ?: return@setOnClickListener
                val darkCtx = ContextThemeWrapper(ctx, R.style.ThemeOverlay_Bilibili_DarkPopup)
                val popup = PopupMenu(darkCtx, anchor, Gravity.TOP)
                val speeds = listOf(0.5f, 1.0f, 2.0f, 3.0f)

                popup.menu.setGroupCheckable(0, true, false)
                speeds.forEachIndexed { i, speed ->
                    popup.menu.add(0, i, 0, "%.1fx".format(speed)).apply {
                        isChecked = speed == ctl.baseSpeed
                    }
                }

                popup.setOnMenuItemClickListener { item ->
                    val speed = speeds.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
                    ctl.setBase(speed)
                    true
                }
                popup.show()
            }
        }
    }

    private fun createQualityButton(ctx: Context): TextView {
        val density = ctx.resources.displayMetrics.density
        val btnSize = (48 * density).toInt()
        val btnMargin = (2 * density).toInt()

        return TextView(ctx).apply {
            text = currentQuality?.description ?: "画质"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                btnSize
            ).also {
                it.leftMargin = btnMargin
                it.rightMargin = btnMargin
            }
            setMinWidth(btnSize)
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            setOnClickListener { anchor ->
                val qualities = availableQualities
                if (qualities.size <= 1) return@setOnClickListener
                val darkCtx = ContextThemeWrapper(ctx, R.style.ThemeOverlay_Bilibili_DarkPopup)
                val popup = PopupMenu(darkCtx, anchor, Gravity.TOP)

                popup.menu.setGroupCheckable(0, true, false)
                qualities.forEachIndexed { i, q ->
                    popup.menu.add(0, i, 0, q.description).apply {
                        isChecked = q.quality == currentQuality?.quality
                    }
                }

                popup.setOnMenuItemClickListener { item ->
                    val quality = qualities.getOrNull(item.itemId)
                    if (quality != null) {
                        onQualitySelected(quality)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}

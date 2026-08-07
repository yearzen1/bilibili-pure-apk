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
import androidx.media3.ui.PlayerView

class PlayerControlBar(
    private val onQualitySelected: (QualityOption) -> Unit
) {
    private var ctx: Context? = null
    private var controlsRow: ViewGroup? = null
    private var speedCtl: SpeedController? = null

    private var currentSpeed = 1.0f
    private var currentQuality: QualityOption? = null
    private var availableQualities: List<QualityOption> = emptyList()

    fun attach(ctx: Context, playerView: PlayerView, speedCtl: SpeedController) {
        this.ctx = ctx
        this.speedCtl = speedCtl

        val settingsId = ctx.resources.getIdentifier("exo_settings", "id", ctx.packageName)
        if (settingsId == 0) return

        val settingsView = playerView.findViewById<View>(settingsId)
        val row = settingsView?.parent as? ViewGroup ?: return
        controlsRow = row

        if (row.findViewWithTag<View>("speed_button") == null) {
            row.addView(createSpeedButton(ctx), 0)
        }
    }

    fun update(newSpeed: Float, newQuality: QualityOption?, newQualities: List<QualityOption>) {
        currentSpeed = newSpeed
        currentQuality = newQuality
        availableQualities = newQualities

        val row = controlsRow ?: return

        row.findViewWithTag<TextView>("speed_button")?.text = "%.1fx".format(currentSpeed)

        if (newQualities.size > 1 && row.findViewWithTag<View>("quality_button") == null) {
            ctx?.let { row.addView(createQualityButton(it), 0) }
        }
        row.findViewWithTag<TextView>("quality_button")?.text = currentQuality?.description ?: "画质"
    }

    private fun createSpeedButton(ctx: Context): TextView {
        val density = ctx.resources.displayMetrics.density
        val btnSize = (48 * density).toInt()
        val btnMargin = (2 * density).toInt()

        return TextView(ctx).apply {
            tag = "speed_button"
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
            tag = "quality_button"
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

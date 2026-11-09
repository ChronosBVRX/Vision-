package com.chronosbvrx.visionplus.ui.games

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Visor del escáner QR: oscurece la cámara y deja una banda horizontal (el
 * celular se usa en vertical y el QR de la tele es ancho) con esquinas azul
 * Vision y una línea de escaneo que recorre la banda.
 */
class PartyScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val scrim = Paint().apply { color = Color.argb(158, 0, 0, 0) }
    private val accent = Paint().apply {
        color = Color.rgb(45, 125, 246)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val softStroke = Paint().apply {
        color = Color.argb(60, 45, 125, 246)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val laser = Paint().apply {
        color = Color.argb(220, 45, 125, 246)
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val bandRect = RectF()
    private val cornerPath = Path()
    private var progress = 0f
    private var rising = true

    private val tick = object : Runnable {
        override fun run() {
            progress += if (rising) 0.02f else -0.02f
            if (progress >= 1f) {
                progress = 1f
                rising = false
            } else if (progress <= 0f) {
                progress = 0f
                rising = true
            }
            invalidate()
            postDelayed(this, 16)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, 16)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bandHeight = height * 0.20f
        val bandWidth = width * 0.84f
        val left = (width - bandWidth) / 2f
        val top = height * 0.40f - bandHeight / 2f
        bandRect.set(left, top, left + bandWidth, top + bandHeight)

        // Velo fuera de la banda (4 paneles).
        canvas.drawRect(0f, 0f, width.toFloat(), bandRect.top, scrim)
        canvas.drawRect(0f, bandRect.bottom, width.toFloat(), height.toFloat(), scrim)
        canvas.drawRect(0f, bandRect.top, bandRect.left, bandRect.bottom, scrim)
        canvas.drawRect(bandRect.right, bandRect.top, width.toFloat(), bandRect.bottom, scrim)

        // Marco suave de la banda.
        val radius = 22f
        canvas.drawRoundRect(bandRect, radius, radius, softStroke)

        // Esquinas.
        val arm = 38f
        cornerPath.reset()
        cornerPath.moveTo(bandRect.left + arm, bandRect.top)
        cornerPath.lineTo(bandRect.left, bandRect.top)
        cornerPath.lineTo(bandRect.left, bandRect.top + arm)
        canvas.drawPath(cornerPath, accent)

        cornerPath.reset()
        cornerPath.moveTo(bandRect.right - arm, bandRect.top)
        cornerPath.lineTo(bandRect.right, bandRect.top)
        cornerPath.lineTo(bandRect.right, bandRect.top + arm)
        canvas.drawPath(cornerPath, accent)

        cornerPath.reset()
        cornerPath.moveTo(bandRect.left + arm, bandRect.bottom)
        cornerPath.lineTo(bandRect.left, bandRect.bottom)
        cornerPath.lineTo(bandRect.left, bandRect.bottom - arm)
        canvas.drawPath(cornerPath, accent)

        cornerPath.reset()
        cornerPath.moveTo(bandRect.right - arm, bandRect.bottom)
        cornerPath.lineTo(bandRect.right, bandRect.bottom)
        cornerPath.lineTo(bandRect.right, bandRect.bottom - arm)
        canvas.drawPath(cornerPath, accent)

        // Línea de escaneo.
        val y = bandRect.top + 10f + progress * (bandRect.height() - 20f)
        canvas.drawLine(bandRect.left + 8f, y, bandRect.right - 8f, y, laser)
    }
}

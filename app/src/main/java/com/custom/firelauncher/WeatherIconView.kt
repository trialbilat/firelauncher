package com.custom.firelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class WxKind { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, RAIN, SNOW, STORM }

/** Small flat weather icon drawn with canvas primitives, so it needs no image assets or emoji fonts. */
class WeatherIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var kind: WxKind = WxKind.CLOUD
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val sunColor = Color.parseColor("#FFC83D")
    private val moonColor = Color.parseColor("#E8ECFF")
    private val cloudColor = Color.parseColor("#F2F5FA")
    private val rainCloudColor = Color.parseColor("#C3CCDB")
    private val stormCloudColor = Color.parseColor("#97A3BA")
    private val rainColor = Color.parseColor("#5AB0FF")
    private val fogColor = Color.parseColor("#D5DCE8")

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)

        when (kind) {
            WxKind.SUN -> drawSun(canvas, 0.5f * s, 0.5f * s, 0.22f * s)
            WxKind.MOON -> drawMoon(canvas, 0.5f * s, 0.5f * s, 0.30f * s)
            WxKind.PARTLY_DAY -> {
                drawSun(canvas, 0.36f * s, 0.34f * s, 0.17f * s)
                drawCloud(canvas, 0.14f * s, 0.36f * s, 0.96f * s, 0.88f * s, cloudColor)
            }
            WxKind.PARTLY_NIGHT -> {
                drawMoon(canvas, 0.35f * s, 0.34f * s, 0.21f * s)
                drawCloud(canvas, 0.14f * s, 0.36f * s, 0.96f * s, 0.88f * s, cloudColor)
            }
            WxKind.CLOUD -> drawCloud(canvas, 0.06f * s, 0.2f * s, 0.94f * s, 0.8f * s, cloudColor)
            WxKind.FOG -> {
                drawCloud(canvas, 0.1f * s, 0.08f * s, 0.9f * s, 0.55f * s, cloudColor)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 0.06f * s
                paint.color = fogColor
                canvas.drawLine(0.18f * s, 0.68f * s, 0.82f * s, 0.68f * s, paint)
                canvas.drawLine(0.28f * s, 0.79f * s, 0.92f * s, 0.79f * s, paint)
                canvas.drawLine(0.14f * s, 0.90f * s, 0.74f * s, 0.90f * s, paint)
                paint.style = Paint.Style.FILL
            }
            WxKind.RAIN -> {
                drawCloud(canvas, 0.08f * s, 0.08f * s, 0.92f * s, 0.62f * s, rainCloudColor)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 0.07f * s
                paint.color = rainColor
                for (x in floatArrayOf(0.32f, 0.52f, 0.72f)) {
                    canvas.drawLine(x * s, 0.70f * s, (x - 0.06f) * s, 0.90f * s, paint)
                }
                paint.style = Paint.Style.FILL
            }
            WxKind.SNOW -> {
                drawCloud(canvas, 0.08f * s, 0.08f * s, 0.92f * s, 0.62f * s, rainCloudColor)
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(0.30f * s, 0.78f * s, 0.05f * s, paint)
                canvas.drawCircle(0.50f * s, 0.90f * s, 0.05f * s, paint)
                canvas.drawCircle(0.70f * s, 0.78f * s, 0.05f * s, paint)
            }
            WxKind.STORM -> {
                drawCloud(canvas, 0.08f * s, 0.06f * s, 0.92f * s, 0.6f * s, stormCloudColor)
                val bolt = Path().apply {
                    moveTo(0.54f * s, 0.56f * s)
                    lineTo(0.40f * s, 0.80f * s)
                    lineTo(0.50f * s, 0.80f * s)
                    lineTo(0.44f * s, 0.98f * s)
                    lineTo(0.66f * s, 0.70f * s)
                    lineTo(0.55f * s, 0.70f * s)
                    lineTo(0.62f * s, 0.56f * s)
                    close()
                }
                paint.style = Paint.Style.FILL
                paint.color = sunColor
                canvas.drawPath(bolt, paint)
            }
        }
        canvas.restore()
    }

    private fun drawSun(c: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = sunColor
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.24f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            c.drawLine(
                cx + dx * r * 1.4f, cy + dy * r * 1.4f,
                cx + dx * r * 1.85f, cy + dy * r * 1.85f, paint
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawMoon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val disc = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val bite = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }
        disc.op(bite, Path.Op.DIFFERENCE)
        paint.style = Paint.Style.FILL
        paint.color = moonColor
        c.drawPath(disc, paint)
    }

    private fun drawCloud(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        val w = r - l
        val h = b - t
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(l + 0.04f * w, t + 0.42f * h, r - 0.04f * w, b)
        c.drawRoundRect(rect, 0.29f * h, 0.29f * h, paint)
        c.drawCircle(l + 0.58f * w, t + 0.42f * h, 0.34f * h, paint)
        c.drawCircle(l + 0.30f * w, t + 0.55f * h, 0.26f * h, paint)
    }
}

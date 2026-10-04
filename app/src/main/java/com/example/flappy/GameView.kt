package com.example.flappy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private enum class State { READY, PLAYING, DEAD }

    private class Pipe(var x: Float, val gapY: Float, var passed: Boolean = false)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("flappy", Context.MODE_PRIVATE)
    private val pipes = mutableListOf<Pipe>()

    private var state = State.READY
    private var u = 1f              // scale unit (screen height / 800)
    private var birdX = 0f
    private var birdY = 0f
    private var birdV = 0f
    private var score = 0
    private var best = prefs.getInt("best", 0)
    private var lastNanos = 0L
    private var time = 0f
    private var groundScroll = 0f
    private var deadAt = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        u = h / 800f
        resetGame()
    }

    private fun resetGame() {
        state = State.READY
        pipes.clear()
        score = 0
        birdX = width * 0.3f
        birdY = height * 0.42f
        birdV = 0f
    }

    private fun flap() {
        birdV = -620f * u
    }

    private fun spawnPipe() {
        val gap = 210f * u
        val margin = 70f * u
        val groundTop = height - 90f * u
        val minY = margin + gap / 2
        val maxY = groundTop - margin - gap / 2
        pipes.add(Pipe(width + 40f * u, Random.nextFloat() * (maxY - minY) + minY))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            when (state) {
                State.READY -> { state = State.PLAYING; flap() }
                State.PLAYING -> flap()
                State.DEAD -> if (System.currentTimeMillis() - deadAt > 500) resetGame()
            }
        }
        return true
    }

    private fun update(dt: Float) {
        time += dt
        val groundTop = height - 90f * u
        val r = 18f * u
        val pipeW = 72f * u
        val gap = 210f * u

        if (state != State.DEAD) groundScroll = (groundScroll + 220f * u * dt) % (40f * u)

        when (state) {
            State.READY -> birdY = height * 0.42f + sin(time * 4f) * 10f * u
            State.PLAYING -> {
                birdV += 1800f * u * dt
                birdY += birdV * dt

                for (p in pipes) p.x -= 220f * u * dt
                if (pipes.isEmpty() || pipes.last().x < width - 260f * u) spawnPipe()
                pipes.removeAll { it.x + pipeW < 0 }

                for (p in pipes) {
                    if (!p.passed && p.x + pipeW < birdX) {
                        p.passed = true
                        score++
                    }
                }

                val bird = RectF(birdX - r * 0.8f, birdY - r * 0.8f, birdX + r * 0.8f, birdY + r * 0.8f)
                var hit = birdY + r >= groundTop || birdY - r <= 0f
                for (p in pipes) {
                    val top = RectF(p.x, 0f, p.x + pipeW, p.gapY - gap / 2)
                    val bottom = RectF(p.x, p.gapY + gap / 2, p.x + pipeW, groundTop)
                    if (RectF.intersects(bird, top) || RectF.intersects(bird, bottom)) hit = true
                }
                if (hit) {
                    state = State.DEAD
                    deadAt = System.currentTimeMillis()
                    if (score > best) {
                        best = score
                        prefs.edit().putInt("best", best).apply()
                    }
                }
            }
            State.DEAD -> {
                if (birdY + r < groundTop) {
                    birdV += 1800f * u * dt
                    birdY = minOf(birdY + birdV * dt, groundTop - r)
                }
            }
        }
    }

    private fun drawPipe(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, capAtBottom: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(96, 190, 60)
        c.drawRect(left, top, right, bottom, paint)
        val capH = 26f * u
        val ext = 5f * u
        paint.color = Color.rgb(70, 160, 40)
        if (capAtBottom) c.drawRect(left - ext, bottom - capH, right + ext, bottom, paint)
        else c.drawRect(left - ext, top, right + ext, top + capH, paint)
    }

    private fun drawText(c: Canvas, text: String, x: Float, y: Float, size: Float) {
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = size
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 8
        paint.color = Color.BLACK
        c.drawText(text, x, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawText(text, x, y, paint)
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else minOf((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        update(dt)

        val w = width.toFloat()
        val h = height.toFloat()
        val groundTop = h - 90f * u
        val pipeW = 72f * u
        val gap = 210f * u

        // sky
        c.drawColor(Color.rgb(112, 197, 206))

        // pipes
        for (p in pipes) {
            drawPipe(c, p.x, 0f, p.x + pipeW, p.gapY - gap / 2, true)
            drawPipe(c, p.x, p.gapY + gap / 2, p.x + pipeW, groundTop, false)
        }

        // ground
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(222, 216, 149)
        c.drawRect(0f, groundTop, w, h, paint)
        paint.color = Color.rgb(115, 191, 46)
        c.drawRect(0f, groundTop, w, groundTop + 14f * u, paint)
        paint.color = Color.rgb(200, 190, 120)
        var gx = -groundScroll
        while (gx < w) {
            c.drawRect(gx, groundTop + 14f * u, gx + 20f * u, groundTop + 28f * u, paint)
            gx += 40f * u
        }

        // bird
        val r = 18f * u
        c.save()
        c.rotate((birdV / (900f * u)).coerceIn(-0.5f, 1.2f) * 45f, birdX, birdY)
        paint.color = Color.rgb(250, 215, 40)
        c.drawCircle(birdX, birdY, r, paint)
        paint.color = Color.WHITE
        c.drawCircle(birdX + r * 0.35f, birdY - r * 0.3f, r * 0.38f, paint)
        paint.color = Color.BLACK
        c.drawCircle(birdX + r * 0.5f, birdY - r * 0.3f, r * 0.17f, paint)
        paint.color = Color.rgb(240, 100, 40)
        c.drawRect(birdX + r * 0.6f, birdY + r * 0.05f, birdX + r * 1.5f, birdY + r * 0.5f, paint)
        c.restore()

        // HUD
        when (state) {
            State.READY -> {
                drawText(c, "FLAPPY CLONE", w / 2, h * 0.2f, 44f * u)
                drawText(c, "Tap to start", w / 2, h * 0.65f, 32f * u)
                drawText(c, "Best: $best", w / 2, h * 0.7f, 26f * u)
            }
            State.PLAYING -> drawText(c, "$score", w / 2, h * 0.15f, 64f * u)
            State.DEAD -> {
                drawText(c, "GAME OVER", w / 2, h * 0.3f, 52f * u)
                drawText(c, "Score: $score   Best: $best", w / 2, h * 0.38f, 30f * u)
                drawText(c, "Tap to retry", w / 2, h * 0.5f, 30f * u)
            }
        }

        postInvalidateOnAnimation()
    }
}

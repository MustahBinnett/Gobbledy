package app.pocketdictate

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import kotlin.math.hypot

enum class ButtonState { LOADING, NO_MODEL, READY, RECORDING, TRANSCRIBING, ERROR }

/**
 * The round floating button. Hold to record, drag to move.
 *
 * A press starts the microphone straight away (so the first word isn't cut
 * off). If your finger moves before HOLD_CONFIRM_MS, it becomes a drag and the
 * audio is thrown away; otherwise the button turns red and buzzes.
 */
@SuppressLint("ViewConstructor")
class MicButtonView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onPressDown()
        fun onHoldConfirmed()
        fun onRelease(holdConfirmed: Boolean)
        fun onDragStart()
        fun onDragBy(dx: Int, dy: Int)
        fun onDragEnd()
    }

    companion object {
        private const val HOLD_CONFIRM_MS = 150L
        private val COLOUR_READY = Color.parseColor("#0B6E69")
        private val COLOUR_RECORDING = Color.parseColor("#E53935")
        private val COLOUR_TRANSCRIBING = Color.parseColor("#F9A825")
        private val COLOUR_IDLE = Color.parseColor("#757575")
        private val COLOUR_ERROR = Color.parseColor("#8E2A2A")
    }

    var state: ButtonState = ButtonState.LOADING
        set(value) {
            field = value
            val spin = value == ButtonState.TRANSCRIBING || value == ButtonState.LOADING ||
                value == ButtonState.RECORDING
            if (spin && !animator.isStarted) animator.start()
            if (!spin && animator.isStarted) animator.cancel()
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }
    private val mic = context.getDrawable(R.drawable.ic_mic)!!.mutate().apply { setTint(Color.WHITE) }
    private val arcRect = RectF()
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var holdConfirmed = false
    private val confirmHold = Runnable {
        if (!dragging) {
            holdConfirmed = true
            listener.onHoldConfirmed()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2f
        fill.color = when (state) {
            ButtonState.READY -> COLOUR_READY
            ButtonState.RECORDING -> COLOUR_RECORDING
            ButtonState.TRANSCRIBING -> COLOUR_TRANSCRIBING
            ButtonState.ERROR -> COLOUR_ERROR
            ButtonState.LOADING, ButtonState.NO_MODEL -> COLOUR_IDLE
        }
        fill.alpha = 235
        val pulse = if (state == ButtonState.RECORDING) 0.06f * kotlin.math.sin(phase * 2 * Math.PI).toFloat() else 0f
        canvas.drawCircle(w / 2, h / 2, r * (0.94f + pulse), fill)

        val iconSize = (r * 1.0f).toInt()
        mic.setBounds(
            (w / 2 - iconSize / 2).toInt(), (h / 2 - iconSize / 2).toInt(),
            (w / 2 + iconSize / 2).toInt(), (h / 2 + iconSize / 2).toInt()
        )
        mic.draw(canvas)

        if (state == ButtonState.TRANSCRIBING || state == ButtonState.LOADING) {
            ring.strokeWidth = r * 0.10f
            val inset = r * 0.16f
            arcRect.set(inset, inset, w - inset, h - inset)
            canvas.drawArc(arcRect, phase * 360f - 90f, 100f, false, ring)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; lastX = downX; lastY = downY
                dragging = false; holdConfirmed = false
                listener.onPressDown()
                postDelayed(confirmHold, HOLD_CONFIRM_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && !holdConfirmed &&
                    hypot(e.rawX - downX, e.rawY - downY) > touchSlop
                ) {
                    dragging = true
                    removeCallbacks(confirmHold)
                    listener.onDragStart()
                }
                if (dragging) {
                    listener.onDragBy((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt())
                    lastX = e.rawX; lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(confirmHold)
                if (dragging) listener.onDragEnd() else listener.onRelease(holdConfirmed)
                dragging = false; holdConfirmed = false
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

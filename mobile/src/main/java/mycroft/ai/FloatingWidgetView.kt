package mycroft.ai

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.preference.PreferenceManager
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.View
import android.view.ViewTreeObserver
import android.view.MotionEvent
import kotlinx.android.synthetic.main.overlay_layout.view.floatingIcon


class FloatingWidgetView(context: Context) : ConstraintLayout(context), View.OnTouchListener {

    private val windowManager: WindowManager
    private val displayMetrics = DisplayMetrics()

    private val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
    )

    private var sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
    private val editor = sharedPref.edit()

    private var x: Int = 0
    private var y: Int = 0
    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private var clickStartTimer: Long = 0


    init {
        View.inflate(context, R.layout.overlay_layout, this)
        setOnTouchListener(this)

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        x = sharedPref.getInt("overlayX", 0)
        if (x == 0) {
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            x = displayMetrics.widthPixels/2-displayMetrics.widthPixels/15
            y = -displayMetrics.heightPixels/4

            floatingIcon.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    floatingIcon.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    x -= floatingIcon.measuredWidth
                }
            })
        }
        else y = sharedPref.getInt("overlayY", 0)

        layoutParams.x = x
        layoutParams.y = y

        windowManager.addView(this, layoutParams)
    }

    companion object {
        private const val CLICK_DELTA = 600
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clickStartTimer = System.currentTimeMillis()

                x = layoutParams.x
                y = layoutParams.y

                touchX = event.rawX
                touchY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                if (event.rawX - touchX < 20 && event.rawY - touchY < 20) {
                    if (System.currentTimeMillis() - clickStartTimer < CLICK_DELTA) startMain()
                    else {
                        // Set overlay Settings off
                        editor.putBoolean("overlaySwitch", false)
                        editor.apply()
                        context.stopService(Intent(context, FloatingWidgetService::class.java))
                    }
                }
                else {
                    editor.putInt("overlayX", layoutParams.x)
                    editor.putInt("overlayY", layoutParams.y)
                    editor.apply()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                layoutParams.x = (x + event.rawX - touchX).toInt()
                layoutParams.y = (y + event.rawY - touchY).toInt()
                windowManager.updateViewLayout(this, layoutParams)
            }
        }
        return true
    }

    private fun startMain() {
        val intent = Intent(context, MainActivity::class.java)
                .putExtra("launchedFromWidget", true)
                .putExtra("autoPromptForSpeech", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }
}
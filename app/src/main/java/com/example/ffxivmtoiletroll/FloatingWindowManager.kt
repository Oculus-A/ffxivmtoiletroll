package com.example.ffxivmtoiletroll
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class FloatingWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableMapOf<String, View>()
    private val TAG = "FloatingWindowManager"

    private fun createLayoutParams(x: Int, y: Int, width: Int, height: Int): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width, height,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    fun addView(id: String, view: View, params: WindowManager.LayoutParams) {
        if (views.containsKey(id)) {
            Log.w(TAG, "View with id '$id' already exists. Removing old one first.")
            removeView(id)
        }
        try {
            windowManager.addView(view, params)
            views[id] = view
            Log.i(TAG, "SUCCESS: View with id '$id' added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to add view to WindowManager for id '$id'", e)
        }
    }

    fun removeView(id: String) {
        views.remove(id)?.let {
            try {
                windowManager.removeView(it)
                Log.i(TAG, "SUCCESS: View with id '$id' removed.")
            } catch (e: Exception) {
                // View might have been removed already, ignore.
            }
        }
    }

    fun showImage(id: String, resId: Int, x: Int, y: Int) {
        val imageView = ImageView(context)
        imageView.setImageResource(resId)
        val params = createLayoutParams(x, y, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        addView(id, imageView, params)
    }

    fun showPreviewArea(id: String, area: Rect) {
        val previewView = View(context)
        // (已修改) 使用XML Drawable资源来设置背景，更稳定
        previewView.setBackgroundResource(R.drawable.preview_border)

        val params = createLayoutParams(area.left, area.top, area.width(), area.height())
        addView(id, previewView, params)
    }

    fun removeAllViews() {
        views.keys.toList().forEach { removeView(it) }
        views.clear()
    }
}

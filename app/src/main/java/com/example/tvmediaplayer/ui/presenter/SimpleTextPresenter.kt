package com.example.tvmediaplayer.ui.presenter

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.example.tvmediaplayer.ui.AppFonts

class SimpleTextPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(28, 18, 28, 18)
            textSize = 20f
            typeface = AppFonts.medium(parent.context)
            setTextColor(Color.parseColor("#EAF2FF"))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
            pivotX = 0f
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) Color.parseColor("#334155") else Color.TRANSPARENT)
            }
        }
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val tv = viewHolder.view as TextView
        val text = item.toString()
        tv.text = text

        if (text.startsWith("【") && text.endsWith("】")) {
            tv.textSize = 24f
            tv.typeface = AppFonts.bold(tv.context)
            tv.setTextColor(Color.parseColor("#F8FAFC"))
            tv.setBackgroundColor(Color.TRANSPARENT)
        } else {
            tv.textSize = 20f
            tv.typeface = AppFonts.medium(tv.context)
            tv.setTextColor(Color.parseColor("#EAF2FF"))
            tv.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}

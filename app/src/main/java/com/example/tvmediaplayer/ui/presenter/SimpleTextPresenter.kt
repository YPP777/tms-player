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
        val width = (parent.resources.displayMetrics.widthPixels * 0.88f).toInt()
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 20, 32, 20)
            textSize = 20f
            typeface = AppFonts.medium(parent.context)
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.parseColor("#0B1220"))
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) Color.parseColor("#1D4ED8") else Color.parseColor("#0B1220"))
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
            tv.setBackgroundColor(Color.parseColor("#0F1B33"))
        } else {
            tv.textSize = 20f
            tv.typeface = AppFonts.medium(tv.context)
            tv.setTextColor(Color.parseColor("#E2E8F0"))
            tv.setBackgroundColor(Color.parseColor("#0B1220"))
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}

package com.humayra.goddess.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.*

object UiKit {
    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun bg(c: Context, fill: String, stroke: String, r: Int = 14) = GradientDrawable().apply {
        setColor(Color.parseColor(fill)); setStroke(dp(c, 1), Color.parseColor(stroke)); cornerRadius = dp(c, r).toFloat()
    }
    fun card(c: Context, parent: LinearLayout, title: String = ""): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; background = bg(c, "#0B1220", "#1E293B", 16)
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) }
        }
        if (title.isNotBlank()) box.addView(TextView(c).apply { text = title; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF60A5FA.toInt()); setPadding(0, 0, 0, dp(c, 8)) })
        parent.addView(box); return box
    }
    fun btn(c: Context, parent: LinearLayout, text: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        val b = TextView(c).apply {
            this.text = text; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            if (primary) setTypeface(null, Typeface.BOLD)
            background = if (primary) bg(c, "#1D4ED8", "#3B82F6", 12) else bg(c, "#131D31", "#26354E", 12)
            setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 7) }
            setOnClickListener { onClick() }
        }
        parent.addView(b); return b
    }
    fun et(c: Context, parent: LinearLayout, hint: String, value: String): EditText = EditText(c).apply {
        this.hint = hint; setText(value); textSize = 13f; setTextColor(Color.WHITE); setHintTextColor(0xFF64748B.toInt())
        background = bg(c, "#0F172A", "#334155", 10); setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 7) }
    }.also { parent.addView(it) }
    fun secretEt(c: Context, parent: LinearLayout, hint: String, value: String): EditText {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bg(c, "#0F172A", "#334155", 10); setPadding(dp(c, 12), dp(c, 4), dp(c, 8), dp(c, 4))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 7) }
        }
        val passT = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val e = EditText(c).apply { this.hint = hint; setText(value); textSize = 13f; setTextColor(Color.WHITE); setHintTextColor(0xFF64748B.toInt()); background = null; inputType = passT; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        var shown = false
        val eye = TextView(c).apply { text = "👁"; textSize = 16f; setPadding(dp(c, 8), dp(c, 6), dp(c, 6), dp(c, 6)); setOnClickListener { shown = !shown; e.inputType = if (shown) InputType.TYPE_CLASS_TEXT else passT; e.setSelection(e.text.length) } }
        row.addView(e); row.addView(eye); parent.addView(row); return e
    }
    fun sw(c: Context, parent: LinearLayout, text: String, checked: Boolean): Switch = Switch(c).apply {
        this.text = text; isChecked = checked; textSize = 13f; setTextColor(0xFFE2E8F0.toInt()); setPadding(0, dp(c, 6), 0, dp(c, 6))
    }.also { parent.addView(it) }
    fun lbl(c: Context, parent: LinearLayout, text: String) = parent.addView(TextView(c).apply { this.text = text; textSize = 12f; setTextColor(0xFF94A3B8.toInt()); setPadding(0, dp(c, 8), 0, dp(c, 2)) })
}

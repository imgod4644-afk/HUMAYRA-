package com.humayra.goddess.ui

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.core.view.*
import com.humayra.goddess.storage.AppConfig

object FullPage {
    fun show(ctx: Context, title: String, actionLabel: String? = null, onAction: ((Dialog) -> Unit)? = null, onBuild: (LinearLayout, Dialog) -> Unit): Dialog {
        val d = Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar)
        val outer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom); i
        }
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F17")); setPadding(20, 20, 24, 20)
        }
        val back = TextView(ctx).apply { text = "←"; textSize = 21f; setTextColor(Color.WHITE); setPadding(10, 4, 24, 4); setOnClickListener { d.dismiss() } }
        val tvTitle = TextView(ctx).apply { text = title; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        bar.addView(back); bar.addView(tvTitle)
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            bar.addView(TextView(ctx).apply { text = actionLabel; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#60A5FA")); setPadding(20, 8, 8, 8); setOnClickListener { onAction(d) } })
        }
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 20, 36, 40) }
        val sv = ScrollView(ctx).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); addView(body) }
        outer.addView(bar); outer.addView(sv); onBuild(body, d); d.setContentView(outer)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK)); setLayout(-1, -1)
            statusBarColor = Color.BLACK; navigationBarColor = Color.BLACK
            if (AppConfig(ctx).flagSecure) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        d.show(); return d
    }
}

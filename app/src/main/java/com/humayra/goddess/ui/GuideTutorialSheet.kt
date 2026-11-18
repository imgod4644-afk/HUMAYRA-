package com.humayra.goddess.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.*

object GuideTutorialSheet {
    fun show(ctx: Context) {
        val guide = """
🌙 HUMAYRA GODDESS OF AI — সম্পূর্ণ গাইড

1️⃣ মাস্টার সুইচ (ওপরে ডানে ⏻):
• সবুজ = সম্পূর্ণ সিস্টেম সক্রিয়।
• লাল/অফ = সব ব্যাকগ্রাউন্ড সার্ভিস, স্ক্রিন রিডার, টেলিগ্রাম ও মাইক সম্পূর্ণ বন্ধ।

2️⃣ ৩ডি অবতার ও স্প্রিং-ব্যাক (3D ট্যাব):
• আঙুল দিয়ে ৩৬০° ঘোরান ও জুম করুন; হাত ছাড়ার ১.৫ সেকেন্ড পর সে নিজেই সামনে ফিরে আসবে।
• Settings → Import .vrm/.glb দিয়ে নতুন চরিত্র ও Wallpaper দিয়ে পেছনের ছবি বদলাতে পারবেন।

3️⃣ Shizuku কানেক্ট করার সহজ নিয়ম (Redmi / HyperOS):
• Developer Options-এ 'USB debugging', 'Wireless debugging' এবং নিচের 'USB debugging (Security settings)' অন করুন।
• পেয়ারিংয়ের আগে Settings → Notifications → Notification style 'Android' করুন (অথবা Split Screen ব্যবহার করুন)।
• Shizuku-র Battery 'No restrictions' রাখুন।

4️⃣ Telegram হিউম্যান বট ব্রিজ:
• Telegram-এ @BotFather থেকে Token নিয়ে Settings-এ বসান ও 'Enable Telegram' অন করুন।
• বটকে Telegram-এ একবার /start লিখলেই কেবল আপনার আইডিতে প্রাইভেটভাবে লক হয়ে যাবে।

5️⃣ 🛡️ CyberShield (প্রাইভেসি ও ব্যাকআপ):
• Real PIN + Decoy PIN (পাশে কেউ থাকলে নকল খালি মোড) + ভুল পিনে Intruder Selfie।
• .hvault ব্যাকআপ: আপনার গোপন পাসওয়ার্ড দিয়ে এনক্রিপ্ট হয়ে Google Drive বা Telegram-এ সেভ হবে।

6️⃣ ভয়েস ও JARVIS কমান্ড উদাহরণ:
• "টর্চ জ্বালাও" | "Proton VPN অন কর" | "YouTube-এ গান চালাও" | "একটা পোর্টফোলিও ওয়েবসাইট বানিয়ে দাও"।
        """.trimIndent()
        val tv = TextView(ctx).apply { text = guide; textSize = 13f; setPadding(44, 24, 44, 24) }
        AlertDialog.Builder(ctx).setTitle("📖 টিউটোরিয়াল ও গাইড").setView(ScrollView(ctx).apply { addView(tv) })
            .setPositiveButton("বুঝেছি", null).show()
    }
}

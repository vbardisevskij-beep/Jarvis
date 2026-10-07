package com.vitalya.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.CAMERA)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,80,48,48) }
        val title = TextView(this).apply { text="JARVIS 3.0"; textSize=38f }
        val info = TextView(this).apply {
            text="""Скажи: «Джарвіс»
Відповідь: «Слухаю, Віталя»

Команди:
• «Подзвони Тані»
• «Напиши Тані у Viber: буду через годину»
• «Напиши Тані у Telegram: буду через годину»
• «Відправ» / «Скасуй»
• «Знайди в TikTok відео про ремонт»
• «Увімкни ліхтарик»
• «Відкрий YouTube»
• «Постав таймер на 10 хвилин»
• «Постав будильник на 7:30»"""
            textSize=18f; setPadding(0,30,0,30)
        }
        val start = Button(this).apply { text="Увімкнути Джарвіса" }
        val stop = Button(this).apply { text="Вимкнути" }
        val battery = Button(this).apply { text="Налаштування батареї / HyperOS" }
        start.setOnClickListener {
            if (perms.any { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }) requestPermissions(perms,10) else startJarvis()
        }
        stop.setOnClickListener { stopService(Intent(this, JarvisService::class.java)) }
        battery.setOnClickListener { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        box.addView(title); box.addView(info); box.addView(start); box.addView(stop); box.addView(battery); setContentView(box)
    }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){ super.onRequestPermissionsResult(r,p,g); if(r==10 && g.all{it==PackageManager.PERMISSION_GRANTED}) startJarvis() }
    private fun startJarvis(){ startForegroundService(Intent(this, JarvisService::class.java)); Toast.makeText(this,"JARVIS слухає",Toast.LENGTH_SHORT).show() }
}

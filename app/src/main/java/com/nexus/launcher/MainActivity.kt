package com.nexus.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private var dark = true
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val bg = if (dark) Color.rgb(12, 16, 30) else Color.rgb(241, 245, 255)
        val fg = if (dark) Color.WHITE else Color.rgb(20, 28, 48)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 36, 22, 20)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "NEXUS"
            textSize = 32f
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.16f
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "GAMING • YOUR SPACE"
            textSize = 12f
            setTextColor(if (dark) Color.rgb(90, 190, 255) else Color.rgb(20, 110, 190))
            letterSpacing = 0.12f
            setPadding(0, 4, 0, 22)
        })

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val theme = Button(this).apply {
            text = if (dark) "☀ Light mode" else "☾ Dark mode"
            setOnClickListener { dark = !dark; render() }
        }
        tools.addView(theme, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tools)

        root.addView(TextView(this).apply {
            text = "YOUR APPS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            setPadding(0, 28, 0, 12)
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val apps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        apps.forEach { info ->
            val appName = info.loadLabel(packageManager).toString()
            val item = Button(this).apply {
                text = "  $appName"
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)?.let(::startActivity)
                }
            }
            list.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 })
        }
        if (apps.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No launchable apps found."
                setTextColor(fg)
            })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }
}

package com.nexus.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {
    private val red = Color.rgb(255, 65, 80)
    private val background = Color.rgb(10, 10, 16)
    private val primary = Color.rgb(248, 247, 250)
    private val muted = Color.rgb(165, 162, 174)
    private lateinit var root: LinearLayout
    private lateinit var drawerContent: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private var startY = 0f
    private var drawerOpen = false

    data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
        if (!getSharedPreferences("nexus", MODE_PRIVATE).getBoolean("launcher_choice_shown", false)) {
            root.postDelayed({ showLauncherChoice() }, 450)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun wallpaperBackground(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(20, 12, 22), Color.rgb(9, 10, 17), Color.rgb(22, 8, 16))
    )

    private fun showHome() {
        drawerOpen = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = wallpaperBackground()
            setPadding(dp(22), dp(42), dp(22), dp(16))
            isClickable = true
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startY = event.y; true }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.y - startY
                        if (delta < -dp(55)) openDrawer()
                        true
                    }
                    else -> true
                }
            }
        }

        val clock = label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 54f, primary, true)
        clock.gravity = Gravity.CENTER
        root.addView(clock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        })

        root.addView(label("N E X U S", 13f, red, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            setPadding(0, dp(8), 0, 0)
        })

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val drawerButton = TextView(this).apply {
            text = "↑   ALL APPS"
            textSize = 12f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(primary)
            background = rounded(Color.argb(55, 255, 255, 255), 22)
            setOnClickListener { openDrawer() }
        }
        root.addView(drawerButton, LinearLayout.LayoutParams(dp(142), dp(42)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(18)
        })

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(48, 255, 255, 255), 26)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val dockApps = findDockApps()
        dockApps.forEach { app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            cell.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
            dock.addView(cell, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(dock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
        setContentView(root)
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180).start()
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun findDockApps(): List<AppEntry> {
        val preferred = listOf(
            listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            listOf("com.android.chrome"),
            listOf("com.sec.android.app.camera", "com.android.camera2")
        )
        val result = mutableListOf<AppEntry>()
        preferred.forEach { candidates ->
            val found = candidates.firstNotNullOfOrNull { pkg ->
                try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    if (packageManager.getLaunchIntentForPackage(pkg) == null) null
                    else AppEntry(packageManager.getApplicationLabel(info).toString(), pkg, packageManager.getApplicationIcon(info))
                } catch (_: Exception) { null }
            }
            if (found != null) result.add(found)
        }
        return result
    }

    private fun allApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { info -> AppEntry(info.loadLabel(packageManager).toString(), info.activityInfo.packageName, info.loadIcon(packageManager)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        val apps = allApps()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = wallpaperBackground()
            setPadding(dp(20), dp(28), dp(20), dp(12))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = label("‹", 34f, primary).apply { setOnClickListener { showHome() } }
        top.addView(back, LinearLayout.LayoutParams(dp(42), dp(48)))
        top.addView(label("ALL APPS", 14f, primary, true).apply { letterSpacing = 0.12f })
        page.addView(top)

        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(muted)
            setTextColor(primary)
            textSize = 15f
            setSingleLine(true)
            background = rounded(Color.rgb(34, 32, 40), 18)
            setPadding(dp(16), 0, dp(16), 0)
        }
        page.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        drawerScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(28))
        }
        drawerScroll.addView(drawerContent)
        page.addView(drawerScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(page)
        renderApps(apps, "")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(apps, s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        page.alpha = 0f
        page.animate().alpha(1f).setDuration(160).start()
    }

    private fun renderApps(apps: List<AppEntry>, query: String) {
        drawerContent.removeAllViews()
        val filtered = apps.filter { it.label.contains(query, true) }
        filtered.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) } }
            }
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(32), dp(32)))
            row.addView(label(app.label, 16f, primary).apply { setPadding(dp(14), 0, 0, 0) })
            drawerContent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        }
        if (filtered.isEmpty()) drawerContent.addView(label("No apps found", 14f, muted))
    }

    private fun showLauncherChoice() {
        if (isFinishing || drawerOpen) return
        AlertDialog.Builder(this)
            .setTitle("Choose your home app")
            .setMessage("Would you like to use Nexus Launcher or keep your current Android launcher?")
            .setPositiveButton("Nexus Launcher") { _, _ ->
                getSharedPreferences("nexus", MODE_PRIVATE).edit().putBoolean("launcher_choice_shown", true).apply()
                requestHomeRole()
            }
            .setNegativeButton("Keep current launcher") { _, _ ->
                getSharedPreferences("nexus", MODE_PRIVATE).edit().putBoolean("launcher_choice_shown", true).apply()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 42)
            }
        } else {
            startActivity(Intent("android.settings.HOME_SETTINGS"))
        }
    }
}

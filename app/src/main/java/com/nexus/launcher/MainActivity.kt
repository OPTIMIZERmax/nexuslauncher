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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val ink = Color.rgb(17, 18, 25)
    private val white = Color.rgb(249, 249, 252)
    private val secondary = Color.rgb(188, 188, 200)
    private val accent = Color.rgb(255, 65, 80)
    private val prefs by lazy { getSharedPreferences("nexus", MODE_PRIVATE) }

    data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 18, 25)
        window.navigationBarColor = Color.rgb(17, 18, 25)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.decorView.systemUiVisibility = 0
        showHome()
        if (!prefs.getBoolean("launcher_choice_shown", false)) {
            window.decorView.postDelayed({ showLauncherChoice() }, 350)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        }

    private fun apps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { info ->
                AppEntry(info.loadLabel(packageManager).toString(), info.activityInfo.packageName, info.loadIcon(packageManager))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun openApp(app: AppEntry) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun showHome() {
        val installed = apps()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(29, 24, 38), Color.rgb(15, 17, 27), Color.rgb(24, 18, 29))
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(18))
        }
        val time = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 48f, white, true)
        header.addView(time)
        header.addView(text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 14f, secondary).apply {
            setPadding(0, dp(6), 0, 0)
        })
        page.addView(header)

        val glance = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(38, 255, 255, 255), 24, Color.argb(28, 255, 255, 255))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        glance.addView(text("NEXUS", 12f, accent, true).apply { letterSpacing = 0.18f })
        glance.addView(text("Your space. Your apps.", 19f, white, true).apply {
            setPadding(0, dp(7), 0, dp(5))
        })
        glance.addView(text("A smoother home for everything you do.", 13f, secondary))
        page.addView(glance, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(24)
        })

        page.addView(text("Favourites", 18f, white, true).apply {
            setPadding(dp(4), 0, 0, dp(14))
        })

        val homeApps = chooseHomeApps(installed)
        val grid = GridLayout(this).apply {
            columnCount = 4
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        homeApps.take(8).forEach { app -> grid.addView(appTile(app, false), GridLayout.LayoutParams().apply {
            width = 0
            height = dp(94)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(2), dp(2), dp(2), dp(4))
        }) }
        page.addView(grid)

        val fill = View(this)
        page.addView(fill, LinearLayout.LayoutParams(1, 0, 1f))

        val allApps = text("⌃", 24f, white, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showDrawer() }
            contentDescription = "Open app drawer"
        }
        page.addView(allApps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(55, 255, 255, 255), 28, Color.argb(30, 255, 255, 255))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        chooseDockApps(installed).forEach { app ->
            dock.addView(appTile(app, true), LinearLayout.LayoutParams(0, dp(66), 1f))
        }
        page.addView(dock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82)).apply {
            topMargin = dp(4)
        })

        setContentView(page)
        page.alpha = 0f
        page.translationY = dp(8).toFloat()
        page.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun chooseHomeApps(installed: List<AppEntry>): List<AppEntry> {
        val chosen = chooseDockApps(installed).toMutableList()
        installed.filterNot { app -> chosen.any { it.packageName == app.packageName } }
            .take(8 - chosen.size).forEach { chosen.add(it) }
        return chosen
    }

    private fun chooseDockApps(installed: List<AppEntry>): List<AppEntry> {
        val candidates = listOf(
            listOf("com.samsung.android.dialer", "com.google.android.dialer"),
            listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
            listOf("com.sec.android.app.camera", "com.android.camera2"),
            listOf("com.android.chrome", "com.sec.android.app.sbrowser")
        )
        val selected = candidates.mapNotNull { group ->
            group.firstNotNullOfOrNull { pkg -> installed.firstOrNull { it.packageName == pkg } }
        }.toMutableList()
        installed.filterNot { app -> selected.any { it.packageName == app.packageName } }
            .take(4 - selected.size).forEach { selected.add(it) }
        return selected.take(4)
    }

    private fun appTile(app: AppEntry, dockMode: Boolean): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { openApp(app) }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(if (dockMode) 38 else 42), dp(if (dockMode) 38 else 42)))
        if (!dockMode) {
            tile.addView(text(app.label, 11f, white).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(7), dp(2), 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        tile.setOnLongClickListener {
            android.widget.Toast.makeText(this, app.label, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        return tile
    }

    private fun showDrawer() {
        val installed = apps()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(29, 24, 38), Color.rgb(15, 17, 27))
            )
            setPadding(dp(20), dp(24), dp(20), dp(12))
        }
        page.addView(text("Apps", 30f, white, true).apply { setPadding(dp(2), dp(12), 0, dp(16)) })
        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(secondary)
            setTextColor(white)
            textSize = 15f
            setSingleLine(true)
            background = rounded(Color.argb(48, 255, 255, 255), 24)
            setPadding(dp(18), 0, dp(18), 0)
        }
        page.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        scroll.addView(grid)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun populate(query: String) {
            grid.removeAllViews()
            installed.filter { it.label.contains(query, true) }.forEach { app ->
                grid.addView(appTile(app, false), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(96)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(1), dp(8), dp(1), dp(4))
                })
            }
        }
        populate("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        val back = text("Home", 14f, white, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.argb(42, 255, 255, 255), 20)
            setOnClickListener { showHome() }
        }
        page.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(8)
        })
        setContentView(page)
        page.alpha = 0f
        page.animate().alpha(1f).setDuration(180).start()
    }

    private fun showLauncherChoice() {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Choose your default launcher")
            .setMessage("Choose Nexus Launcher for your home screen, or keep using your current Samsung launcher.")
            .setPositiveButton("Nexus Launcher") { _, _ ->
                prefs.edit().putBoolean("launcher_choice_shown", true).apply()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val manager = getSystemService(RoleManager::class.java)
                    if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), 42)
                    }
                } else {
                    startActivity(Intent("android.settings.HOME_SETTINGS"))
                }
            }
            .setNegativeButton("Samsung One UI Home") { _, _ ->
                prefs.edit().putBoolean("launcher_choice_shown", true).apply()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}

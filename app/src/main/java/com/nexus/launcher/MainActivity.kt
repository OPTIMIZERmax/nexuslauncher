package com.nexus.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
    private val red = Color.rgb(255, 65, 80)
    private val background = Color.rgb(12, 12, 18)
    private val primary = Color.rgb(246, 244, 246)
    private val muted = Color.rgb(160, 158, 168)
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var alphabetRail: LinearLayout
    private var query = ""
    private val sectionViews = mutableMapOf<Char, View>()
    private val favorites = mutableSetOf<String>()

    data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(getSharedPreferences("nexus", MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun allApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { info ->
                AppEntry(info.loadLabel(packageManager).toString(), info.activityInfo.packageName, info.loadIcon(packageManager))
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun render() {
        val apps = allApps()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(background)
            setPadding(dp(22), dp(28), dp(10), dp(12))
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val clock = label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 42f, primary, true)
        content.addView(clock)
        content.addView(label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()), 11f, muted).apply {
            letterSpacing = 0.08f
            setPadding(0, dp(2), 0, dp(20))
        })
        content.addView(label("NEXUS", 13f, red, true).apply {
            letterSpacing = 0.24f
            setPadding(0, 0, 0, dp(12))
        })

        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(muted)
            setTextColor(primary)
            textSize = 15f
            singleLine = true
            background = rounded(Color.rgb(34, 32, 40), 18)
            setPadding(dp(16), 0, dp(16), 0)
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                buildAppList(apps)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        alphabetRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), 0, dp(10))
        }
        outer.addView(alphabetRail, LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(outer)
        buildAppList(apps)
    }

    private fun buildAppList(apps: List<AppEntry>) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), dp(6), dp(28))
        }
        sectionViews.clear()
        val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
        if (query.isEmpty()) {
            val pinned = filtered.filter { favorites.contains(it.packageName) }
            if (pinned.isNotEmpty()) {
                list.addView(label("FAVOURITES", 11f, red, true).apply {
                    letterSpacing = 0.12f
                    setPadding(0, 0, 0, dp(8))
                })
                pinned.forEach { list.addView(appRow(it), rowParams()) }
                list.addView(label("ALL APPS", 11f, muted, true).apply {
                    letterSpacing = 0.12f
                    setPadding(0, dp(22), 0, dp(8))
                })
            }
        }

        val groups = filtered.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
        groups.forEach { (letter, entries) ->
            val heading = label(letter.toString(), 13f, red, true).apply {
                setPadding(0, dp(12), 0, dp(5))
            }
            sectionViews[letter] = heading
            list.addView(heading)
            entries.forEach { list.addView(appRow(it), rowParams()) }
        }
        if (filtered.isEmpty()) {
            list.addView(label("No apps found", 14f, muted).apply { setPadding(0, dp(24), 0, 0) })
        }
        scroll.removeAllViews()
        scroll.addView(list)
        buildAlphabetRail(groups.keys.toList())
    }

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))

    private fun appRow(app: AppEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(6), 0)
        }
        val icon = ImageView(this).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }
        row.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))
        val name = label(app.label, 16f, primary).apply { setPadding(dp(14), 0, 0, 0) }
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (favorites.contains(app.packageName)) row.addView(label("★", 17f, red))
        row.setOnClickListener {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
        }
        row.setOnLongClickListener {
            if (!favorites.add(app.packageName)) favorites.remove(app.packageName)
            getSharedPreferences("nexus", MODE_PRIVATE).edit().putStringSet("favorites", favorites.toSet()).apply()
            buildAppList(allApps())
            true
        }
        return row
    }

    private fun buildAlphabetRail(letters: List<Char>) {
        alphabetRail.removeAllViews()
        if (query.isNotEmpty()) return
        val railLetters = listOf('★') + ('A'..'Z').toList()
        railLetters.forEach { letter ->
            val item = label(letter.toString(), 9f, if (letters.contains(letter) || letter == '★') red else Color.rgb(90, 88, 98), true).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    if (letter == '★') {
                        scroll.post { scroll.smoothScrollTo(0, 0) }
                    } else {
                        val target = sectionViews[letter]
                        if (target != null) scroll.post { scroll.smoothScrollTo(0, target.top) }
                    }
                }
            }
            alphabetRail.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}

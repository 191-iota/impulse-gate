package app.impulsegate

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {

    private data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

    private val prefs by lazy { getSharedPreferences(BlockerService.PREFS, Context.MODE_PRIVATE) }
    private lateinit var statusText: TextView
    private lateinit var statusButton: TextView

    private val bg = Color.parseColor("#0A0B0D")
    private val cardBg = Color.parseColor("#14161A")
    private val border = Color.parseColor("#2A2E35")
    private val dimText = Color.parseColor("#8E939B")
    private val accent = Color.parseColor("#D8DCE2")

    private fun cubeDrawable(radius: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.parseColor("#E2E5EA"),
            Color.parseColor("#888E98"),
            Color.parseColor("#41464E"),
        )
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1f), Color.parseColor("#565B63"))
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val rootColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20f), dp(24f), dp(20f), 0)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(View(this).apply {
            background = cubeDrawable(6f)
            layoutParams = LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
                rightMargin = dp(12f)
            }
        })
        titleRow.addView(TextView(this).apply {
            text = "Impulse Gate"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        rootColumn.addView(titleRow)
        rootColumn.addView(TextView(this).apply {
            text = "Gated apps open behind a wall until you type “${BlockerService.PHRASE}”."
            setTextColor(dimText)
            textSize = 14f
            setPadding(0, dp(6f), 0, dp(16f))
        })

        // Status card: shows whether the accessibility service is on, with a settings shortcut.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(cardBg)
                setStroke(dp(1f), border)
            }
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusButton = TextView(this).apply {
            text = "ENABLE"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(accent)
            }
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        card.addView(statusText)
        card.addView(statusButton)
        rootColumn.addView(card)

        rootColumn.addView(TextView(this).apply {
            text = "CHOOSE APPS TO GATE"
            setTextColor(dimText)
            textSize = 12f
            letterSpacing = 0.08f
            setPadding(dp(4f), dp(20f), 0, dp(8f))
        })

        val list = ListView(this).apply {
            divider = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        rootColumn.addView(list)
        setContentView(rootColumn)

        Thread {
            val apps = loadLaunchableApps()
            runOnUiThread { list.adapter = AppAdapter(apps) }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        statusText.text = if (on) "Gate is active" else "Gate is OFF. Enable the\naccessibility service"
        statusText.setTextColor(if (on) accent else Color.WHITE)
        statusButton.text = if (on) "SETTINGS" else "ENABLE"
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, BlockerService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map {
                AppEntry(
                    label = packageManager.getApplicationLabel(it).toString(),
                    pkg = it.packageName,
                    icon = packageManager.getApplicationIcon(it)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private inner class AppAdapter(private val apps: List<AppEntry>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row: LinearLayout
            val icon: ImageView
            val label: TextView
            val check: CheckBox
            if (convertView == null) {
                row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4f), dp(10f), dp(4f), dp(10f))
                }
                icon = ImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
                }
                label = TextView(this@MainActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(dp(14f), 0, dp(8f), 0)
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                check = CheckBox(this@MainActivity).apply {
                    isClickable = false
                    isFocusable = false
                    buttonTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(accent, Color.parseColor("#4A4F57"))
                    )
                }
                row.addView(icon)
                row.addView(label)
                row.addView(check)
                row.tag = Triple(icon, label, check)
            } else {
                row = convertView as LinearLayout
                @Suppress("UNCHECKED_CAST")
                val views = row.tag as Triple<ImageView, TextView, CheckBox>
                icon = views.first
                label = views.second
                check = views.third
            }

            val app = apps[position]
            icon.setImageDrawable(app.icon)
            label.text = app.label
            val blocked = prefs.getStringSet(BlockerService.KEY_BLOCKED, emptySet()) ?: emptySet()
            check.isChecked = app.pkg in blocked
            row.setOnClickListener {
                val current =
                    (prefs.getStringSet(BlockerService.KEY_BLOCKED, emptySet()) ?: emptySet())
                        .toMutableSet()
                val nowChecked = if (app.pkg in current) {
                    current.remove(app.pkg); false
                } else {
                    current.add(app.pkg); true
                }
                prefs.edit().putStringSet(BlockerService.KEY_BLOCKED, current).apply()
                check.isChecked = nowChecked
            }
            return row
        }
    }
}

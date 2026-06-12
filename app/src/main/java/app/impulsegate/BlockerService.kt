package app.impulsegate

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class BlockerService : AccessibilityService() {

    companion object {
        const val PREFS = "gate"
        const val KEY_BLOCKED = "blocked"
        const val PHRASE = "i want this"
        private const val TAG = "ImpulseGate"
        private const val IME_CACHE_MS = 30_000L
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private var overlay: FrameLayout? = null
    private var gatedPkg: String? = null     // package the visible overlay is currently gating
    private var unlockedPkg: String? = null  // package the user typed the phrase for; cleared on leave

    private var imePackages: Set<String> = emptySet()
    private var imeCachedAt = 0L

    private var homePackages: Set<String> = emptySet()
    private var homeCachedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Never crash: a throwing accessibility service is shut off by the system
        // until manually re-enabled, which would silently drop every gate.
        try {
            handleEvent(event)
        } catch (e: Exception) {
            // Swallowed so the service survives, but loud in logcat: a persistent
            // failure here would otherwise be an invisible fail-open.
            Log.w(TAG, "handleEvent failed", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Our own windows (the overlay itself, the settings screen) are not app switches.
        if (pkg == packageName) return
        // Neither is the keyboard popping up, nor the notification shade / quick settings.
        // But SystemUI-hosted recents (pre-Android 9, some 3-button setups) IS leaving.
        if (pkg == "com.android.systemui") {
            if (event.className?.toString()?.contains("recents", ignoreCase = true) == true) {
                unlockedPkg = null
                hideOverlay()
            }
            return
        }
        if (pkg in enabledImePackages()) return

        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        if (pkg in blocked) {
            if (pkg == unlockedPkg) return            // already passed the gate this visit
            if (pkg == gatedPkg && overlay != null) return // gate already up for this app
            unlockedPkg = null
            showOverlay(pkg)
        } else if (isAppSwitch(pkg)) {
            // The user really went somewhere else (another app, or home): re-arm the gate.
            unlockedPkg = null
            hideOverlay()
        }
        // Otherwise: a transient system window (permission dialog, share sheet, volume
        // panel...) appeared over the gated app. Not a switch, so keep the unlock.
    }

    // True only for windows that mean "the user left": the home screen / recents, or
    // another openable app. System dialogs and panels are not launchable and stay false.
    private fun isAppSwitch(pkg: String): Boolean =
        pkg in homePkgs() || packageManager.getLaunchIntentForPackage(pkg) != null

    private fun homePkgs(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - homeCachedAt > IME_CACHE_MS) {
            val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            homePackages = packageManager
                .queryIntentActivities(home, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
            homeCachedAt = now
        }
        return homePackages
    }

    override fun onInterrupt() {}

    // Rebuild the overlay when the screen geometry changes (rotation, fold/unfold):
    // the pre-30 window has an explicit pixel size, and the layout paddings are
    // computed from the display metrics at build time.
    private var lastConfig = Triple(Configuration.ORIENTATION_UNDEFINED, 0, 0)

    private fun configKey(c: Configuration) =
        Triple(c.orientation, c.screenWidthDp, c.screenHeightDp)

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastConfig = configKey(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val key = configKey(newConfig)
        if (key == lastConfig) return
        lastConfig = key
        val pkg = gatedPkg ?: return
        try {
            showOverlay(pkg)
        } catch (e: Exception) {
            Log.w(TAG, "overlay rebuild failed", e)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun enabledImePackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - imeCachedAt > IME_CACHE_MS) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
            imeCachedAt = now
        }
        return imePackages
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).toInt()

    private fun showOverlay(pkg: String) {
        hideOverlay()

        val root = object : FrameLayout(this) {
            // Swallow Back so the gate cannot be dismissed; Home still works as the way out.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
                return super.dispatchKeyEvent(event)
            }
        }
        root.setBackgroundColor(Color.parseColor("#0A0B0D")) // fully opaque: nothing shows through

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32f), resources.displayMetrics.heightPixels / 6, dp(32f), 0)
        }

        val cube = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#E2E5EA"),
                    Color.parseColor("#888E98"),
                    Color.parseColor("#41464E"),
                )
            ).apply {
                cornerRadius = dp(10f).toFloat()
                setStroke(dp(1f), Color.parseColor("#565B63"))
            }
        }

        val title = TextView(this).apply {
            text = appLabel(pkg)
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(16f), 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = "Take a breath. If you still mean it, type:"
            setTextColor(Color.parseColor("#8E939B"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
        }

        val phrase = TextView(this).apply {
            text = PHRASE
            setTextColor(Color.parseColor("#D8DCE2"))
            textSize = 18f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(20f))
        }

        val input = EditText(this).apply {
            hint = "type here"
            setHintTextColor(Color.parseColor("#4A4F57"))
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.MONOSPACE
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(Color.parseColor("#14161A"))
                setStroke(dp(1f), Color.parseColor("#2A2E35"))
            }
            setPadding(dp(18f), dp(14f), dp(18f), dp(14f))
        }
        // Typing is the friction: no autofill, no long-press menu, no pasting.
        input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        input.isLongClickable = false
        val noEditActions = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
        input.customSelectionActionModeCallback = noEditActions
        input.customInsertionActionModeCallback = noEditActions
        input.addTextChangedListener(object : TextWatcher {
            private var inserted = 0
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                inserted = count
            }
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                if (inserted >= 8) {
                    // One edit inserting this much is a clipboard insert, not typing.
                    // Measured on the inserted span, not net growth, so pasting over
                    // a selection can't hide the jump.
                    inserted = 0
                    input.setText("")
                    input.hint = "type it by hand"
                    return
                }
                val typed = text.trim().replace(Regex("\\s+"), " ").lowercase()
                if (typed == PHRASE) {
                    // Mark unlocked BEFORE removing the window: the removal itself can
                    // fire a window-state event for the gated app.
                    unlockedPkg = pkg
                    hideOverlay()
                }
            }
        })

        column.addView(cube, LinearLayout.LayoutParams(dp(64f), dp(64f)))
        column.addView(title)
        column.addView(subtitle)
        column.addView(phrase)
        column.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            windowAnimations = 0 // appear instantly, no fade-in revealing the app behind
            if (Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0 // extend under status and navigation bars
            } else {
                // Pre-30 window policy keeps MATCH_PARENT overlays above the nav bar,
                // leaving a see-through strip. Size the window to the real display and
                // lift the layout limit so it covers everything.
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(dm)
                width = dm.widthPixels
                height = dm.heightPixels
            }
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(root, lp)
        } catch (e: Exception) {
            return // window token gone (service shutting down); fail closed on the next event
        }
        overlay = root
        gatedPkg = pkg

        input.requestFocus()
        input.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun hideOverlay() {
        val view = overlay ?: run { gatedPkg = null; return }
        overlay = null
        gatedPkg = null
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // already detached
        }
    }
}

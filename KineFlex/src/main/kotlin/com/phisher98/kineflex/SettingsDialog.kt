package com.phisher98.kineflex

import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.CommonActivity.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SettingsDialog for KineFlex extension.
 * Provides configuration for KineFlex API key, TMDB credentials, and custom JSON feeds.
 * Optimized for both touch mobile devices and Android TV D-pad navigation.
 */
class SettingsDialog(
    private val sharedPref: SharedPreferences,
    private val onSavedCallback: (() -> Unit)? = null
) : DialogFragment() {

    private lateinit var kineFlexKeyInput: EditText
    private lateinit var kineFlexStatusText: TextView
    private lateinit var tmdbKeyInput: EditText
    private lateinit var tmdbStatusText: TextView

    private lateinit var latestNowInput: EditText
    private lateinit var trendingNowInput: EditText
    private lateinit var premiumInput: EditText
    private lateinit var otherInput: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val rootScrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#141414"))
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Header
        val headerTitle = TextView(context).apply {
            text = "KineFlex Settings"
            textSize = 22f
            setTextColor(Color.parseColor("#FFFFFF"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(12))
        }
        contentLayout.addView(headerTitle)

        // 2. KineFlex API Key Card
        val kineFlexCard = createCard(context) {
            val title = createCardTitle(context, "1. KineFlex API Key")
            addView(title)

            val desc = createCardDesc(
                context,
                "Required for stream resolution. Register at https://api.kineflex.site to get your API key with 100 free points."
            )
            addView(desc)

            val savedKineKey = KineFlexConfig.getKineFlexApiKey(sharedPref)
            kineFlexStatusText = TextView(context).apply {
                text = "Status: ${if (savedKineKey != null) "Configured (${KineFlexConfig.maskSecret(savedKineKey)})" else "Not configured"}"
                setTextColor(if (savedKineKey != null) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(6))
            }
            addView(kineFlexStatusText)

            kineFlexKeyInput = createInputField(context, "Enter KineFlex API key (kine_live_...)").apply {
                if (!savedKineKey.isNullOrEmpty()) {
                    setText(savedKineKey)
                }
                transformationMethod = PasswordTransformationMethod.getInstance()
            }
            addView(kineFlexKeyInput)

            val showKeyCheck = CheckBox(context).apply {
                text = "Show API key"
                setTextColor(Color.parseColor("#BBBBBB"))
                setOnCheckedChangeListener { _, isChecked ->
                    val sel = kineFlexKeyInput.selectionEnd
                    kineFlexKeyInput.transformationMethod = if (isChecked) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
                    kineFlexKeyInput.setSelection(sel)
                }
                makeTvFocusable()
            }
            addView(showKeyCheck)

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }

            val testBtn = createButton(context, "Test Connection", "#2196F3").apply {
                setOnClickListener {
                    val key = kineFlexKeyInput.text.toString().trim()
                    if (key.isEmpty()) {
                        showToast("Please enter an API key to test")
                        return@setOnClickListener
                    }
                    isEnabled = false
                    text = "Testing..."
                    lifecycleScope.launch {
                        val (success, message) = withContext(Dispatchers.IO) {
                            KineFlexResolver.testConnection(key)
                        }
                        isEnabled = true
                        text = "Test Connection"
                        showToast(message)
                        if (success) {
                            kineFlexStatusText.text = "Status: Verified"
                            kineFlexStatusText.setTextColor(Color.parseColor("#4CAF50"))
                        } else {
                            kineFlexStatusText.text = "Status: Verification failed"
                            kineFlexStatusText.setTextColor(Color.parseColor("#F44336"))
                        }
                    }
                }
            }

            val clearBtn = createButton(context, "Clear Key", "#555555").apply {
                setOnClickListener {
                    kineFlexKeyInput.setText("")
                    KineFlexConfig.clearKineFlexApiKey(sharedPref)
                    kineFlexStatusText.text = "Status: Not configured"
                    kineFlexStatusText.setTextColor(Color.parseColor("#FF9800"))
                    showToast("KineFlex API key cleared")
                }
            }

            btnRow.addView(testBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpacer(btnRow, dp(8))
            btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnRow)
        }
        contentLayout.addView(kineFlexCard)
        addSpacer(contentLayout, dp(12))

        // 3. TMDB Credential Card
        val tmdbCard = createCard(context) {
            val title = createCardTitle(context, "2. TMDB Credential")
            addView(title)

            val desc = createCardDesc(
                context,
                "Required for movie/TV search & metadata. Get your free v3 API key or v4 Read-Only Access Token at https://www.themoviedb.org/settings/api."
            )
            addView(desc)

            val savedTmdbKey = KineFlexConfig.getTmdbCredential(sharedPref)
            tmdbStatusText = TextView(context).apply {
                text = "Status: ${if (savedTmdbKey != null) "Configured (${KineFlexConfig.maskSecret(savedTmdbKey)})" else "Not configured"}"
                setTextColor(if (savedTmdbKey != null) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(6))
            }
            addView(tmdbStatusText)

            tmdbKeyInput = createInputField(context, "Enter TMDB API Key or v4 Access Token").apply {
                if (!savedTmdbKey.isNullOrEmpty()) {
                    setText(savedTmdbKey)
                }
                transformationMethod = PasswordTransformationMethod.getInstance()
            }
            addView(tmdbKeyInput)

            val showTmdbCheck = CheckBox(context).apply {
                text = "Show TMDB credential"
                setTextColor(Color.parseColor("#BBBBBB"))
                setOnCheckedChangeListener { _, isChecked ->
                    val sel = tmdbKeyInput.selectionEnd
                    tmdbKeyInput.transformationMethod = if (isChecked) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
                    tmdbKeyInput.setSelection(sel)
                }
                makeTvFocusable()
            }
            addView(showTmdbCheck)

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }

            val testBtn = createButton(context, "Test TMDB", "#2196F3").apply {
                setOnClickListener {
                    val key = tmdbKeyInput.text.toString().trim()
                    if (key.isEmpty()) {
                        showToast("Please enter a TMDB credential to test")
                        return@setOnClickListener
                    }
                    isEnabled = false
                    text = "Testing..."
                    val client = TmdbClient { key }
                    lifecycleScope.launch {
                        val (success, message) = withContext(Dispatchers.IO) {
                            client.validateCredential(key)
                        }
                        isEnabled = true
                        text = "Test TMDB"
                        showToast(message)
                        if (success) {
                            tmdbStatusText.text = "Status: Verified"
                            tmdbStatusText.setTextColor(Color.parseColor("#4CAF50"))
                        } else {
                            tmdbStatusText.text = "Status: Verification failed"
                            tmdbStatusText.setTextColor(Color.parseColor("#F44336"))
                        }
                    }
                }
            }

            val clearBtn = createButton(context, "Clear Credential", "#555555").apply {
                setOnClickListener {
                    tmdbKeyInput.setText("")
                    KineFlexConfig.clearTmdbCredential(sharedPref)
                    tmdbStatusText.text = "Status: Not configured"
                    tmdbStatusText.setTextColor(Color.parseColor("#FF9800"))
                    showToast("TMDB credential cleared")
                }
            }

            btnRow.addView(testBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpacer(btnRow, dp(8))
            btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnRow)
        }
        contentLayout.addView(tmdbCard)
        addSpacer(contentLayout, dp(12))

        // 4. Home Category JSON Feeds Card
        val feedsCard = createCard(context) {
            val title = createCardTitle(context, "3. Home Category Feeds (JSON URLs)")
            addView(title)

            val desc = createCardDesc(
                context,
                "Configure external JSON feed URLs for each home category. Leave empty to use defaults or keep section inactive."
            )
            addView(desc)

            addView(createInputLabel(context, "Latest Now URL:"))
            latestNowInput = createInputField(context, KineFlexConfig.DEFAULT_LATEST_NOW_URL).apply {
                setText(KineFlexConfig.getCategoryUrl("latest_now", sharedPref))
            }
            addView(latestNowInput)

            addView(createInputLabel(context, "Trending Now URL:"))
            trendingNowInput = createInputField(context, "Enter JSON feed URL for Trending Now").apply {
                setText(KineFlexConfig.getCategoryUrl("trending_now", sharedPref))
            }
            addView(trendingNowInput)

            addView(createInputLabel(context, "Premium URL:"))
            premiumInput = createInputField(context, "Enter JSON feed URL for Premium").apply {
                setText(KineFlexConfig.getCategoryUrl("premium", sharedPref))
            }
            addView(premiumInput)

            addView(createInputLabel(context, "Other URL:"))
            otherInput = createInputField(context, "Enter JSON feed URL for Other").apply {
                setText(KineFlexConfig.getCategoryUrl("other", sharedPref))
            }
            addView(otherInput)

            val resetFeedsBtn = createButton(context, "Reset Feeds to Defaults", "#555555").apply {
                setOnClickListener {
                    latestNowInput.setText(KineFlexConfig.DEFAULT_LATEST_NOW_URL)
                    trendingNowInput.setText("")
                    premiumInput.setText("")
                    otherInput.setText("")
                    KineFlexConfig.resetCategoryUrls(sharedPref)
                    showToast("Category feeds reset to defaults")
                }
            }
            addView(resetFeedsBtn)
        }
        contentLayout.addView(feedsCard)
        addSpacer(contentLayout, dp(16))

        // 5. Action Buttons (Save & Cancel)
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = createButton(context, "Cancel", "#444444").apply {
            setOnClickListener {
                dismiss()
            }
        }

        val saveBtn = createButton(context, "Save & Apply", "#E50914").apply {
            setOnClickListener {
                saveSettings()
            }
        }

        actionRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addSpacer(actionRow, dp(10))
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        contentLayout.addView(actionRow)

        rootScrollView.addView(contentLayout)
        return rootScrollView
    }

    private fun saveSettings() {
        val kineKey = kineFlexKeyInput.text.toString().trim()
        val tmdbKey = tmdbKeyInput.text.toString().trim()

        if (kineKey.isNotEmpty()) {
            KineFlexConfig.setKineFlexApiKey(kineKey, sharedPref)
        } else {
            KineFlexConfig.clearKineFlexApiKey(sharedPref)
        }

        if (tmdbKey.isNotEmpty()) {
            KineFlexConfig.setTmdbCredential(tmdbKey, sharedPref)
        } else {
            KineFlexConfig.clearTmdbCredential(sharedPref)
        }

        KineFlexConfig.setCategoryUrl("latest_now", latestNowInput.text.toString().trim(), sharedPref)
        KineFlexConfig.setCategoryUrl("trending_now", trendingNowInput.text.toString().trim(), sharedPref)
        KineFlexConfig.setCategoryUrl("premium", premiumInput.text.toString().trim(), sharedPref)
        KineFlexConfig.setCategoryUrl("other", otherInput.text.toString().trim(), sharedPref)

        showToast("KineFlex settings saved successfully")
        onSavedCallback?.invoke()
        dismiss()
    }

    // Helper UI builders with TV D-Pad navigation support
    private fun createCard(context: android.content.Context, block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1F1F1F"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#333333"))
            }
            background = drawable
            block()
        }
    }

    private fun createCardTitle(context: android.content.Context, title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(4))
        }
    }

    private fun createCardDesc(context: android.content.Context, desc: String): TextView {
        return TextView(context).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, dp(8))
        }
    }

    private fun createInputLabel(context: android.content.Context, label: String): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#DDDDDD"))
            setPadding(0, dp(6), 0, dp(2))
        }
    }

    private fun createInputField(context: android.content.Context, hintText: String): EditText {
        return EditText(context).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2B2B"))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.parseColor("#444444"))
            }
            background = bg
            inputType = InputType.TYPE_CLASS_TEXT
            makeTvFocusable()
        }
    }

    private fun createButton(context: android.content.Context, textStr: String, bgColor: String): Button {
        return Button(context).apply {
            text = textStr
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            makeTvFocusable()
        }
    }

    private fun View.makeTvFocusable() {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val focusBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#3A3A3A"))
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(2), Color.parseColor("#E50914"))
                }
                v.background = focusBg
            } else {
                val defaultBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2B2B2B"))
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.parseColor("#444444"))
                }
                v.background = defaultBg
            }
        }
    }

    private fun addSpacer(parent: LinearLayout, sizePx: Int) {
        val spacer = View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
        parent.addView(spacer)
    }

    private fun dp(dpVal: Int): Int {
        return (dpVal * resources.displayMetrics.density).toInt()
    }
}

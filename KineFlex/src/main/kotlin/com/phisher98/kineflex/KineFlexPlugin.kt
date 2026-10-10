package com.phisher98.kineflex

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Entry point for the KineFlex CloudStream extension plugin.
 */
@CloudstreamPlugin
class KineFlexPlugin : Plugin() {

    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences(KineFlexConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val provider = KineFlexProvider(this, sharedPref)
        registerMainAPI(provider)

        val activity = context.findActivity()
        openSettings = {
            activity?.let {
                val dialog = SettingsDialog(sharedPref)
                dialog.show(it.supportFragmentManager, "KineFlexSettingsDialog")
            }
        }
    }

    private fun Context.findActivity(): AppCompatActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is AppCompatActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

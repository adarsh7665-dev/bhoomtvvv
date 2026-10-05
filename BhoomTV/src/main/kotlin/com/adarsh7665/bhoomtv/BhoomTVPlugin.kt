package com.adarsh7665.bhoomtv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BhoomTVPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BhoomTVProvider())
    }
}

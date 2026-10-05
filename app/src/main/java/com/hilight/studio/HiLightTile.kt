package com.hilight.studio

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HiLightTile : TileService() {
    private val store by lazy { Store.get(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        render()
        listeningJob?.cancel()
        listeningJob = scope.launch {
            combine(
                store.flashlightActive,
                store.flashlightBrightness
            ) { active, brightness ->
                active to brightness
            }.collect {
                render()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        store.toggleFlashlight()
        render()
        refresh(this)
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = store.flashlightActive.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = android.graphics.drawable.Icon.createWithResource(
            this,
            if (on) R.drawable.ic_flashlight else R.drawable.ic_flashlight_off
        )
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }

    companion object {
        fun refresh(ctx: android.content.Context) {
            runCatching {
                requestListeningState(ctx, ComponentName(ctx, HiLightTile::class.java))
            }
        }
    }
}

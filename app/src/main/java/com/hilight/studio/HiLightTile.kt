package com.hilight.studio

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class HiLightTile : TileService() {
    private val store by lazy { Store.get(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        // The status flow is only polled while the app is open; sample it once for this panel.
        store.refreshStatus()
        render()
        listeningJob?.cancel()
        listeningJob = scope.launch {
            combine(
                store.flashlightActive,
                store.status,
            ) { active, status ->
                active to store.isRendererConnectedForUi(status)
            }.distinctUntilChanged().collect {
                render()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        listeningJob = null
        scope.cancel()
        super.onDestroy()
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
        tile.subtitle = getString(
            when {
                on -> R.string.tile_on
                // Turning the flashlight on would light nothing; say so instead of a plain "Off".
                !store.isRendererConnectedForUi(store.status.value) -> R.string.tile_no_renderer
                else -> R.string.tile_off
            }
        )
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

package com.hilight.studio

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
            store.flashlightActive.collect {
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
        HiLightTile.refresh(this)
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = store.flashlightActive.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_flashlight)
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

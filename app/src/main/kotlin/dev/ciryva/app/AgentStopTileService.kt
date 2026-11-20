package dev.ciryva.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Accessible from other apps when the owner adds "CiRYVA STOP ALL"
 * to Android Quick Settings. Calls the same hard stop as both app screens.
 */
class AgentStopTileService : TileService() {
    override fun onClick() {
        super.onClick()
        AgentStopSwitch.stopAll()
        update()
    }
    override fun onStartListening() {
        super.onStartListening()
        update()
    }
    private fun update() {
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = "CiRYVA STOP ALL"
            tile.contentDescription = "Immediately cancel CiRYVA agent work and chat streaming"
            tile.updateTile()
        }
    }
}

package tech.thatgravyboat.craftify.ui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import gg.essential.universal.UScreen
import tech.thatgravyboat.craftify.Initializer
import tech.thatgravyboat.craftify.config.Config
import tech.thatgravyboat.craftify.platform.compat.obsoverlay.ObsOverlayCompat
import tech.thatgravyboat.craftify.services.ads.AdManager
import tech.thatgravyboat.craftify.themes.library.ScreenshotScreen
import tech.thatgravyboat.craftify.ui.enums.Anchor
import tech.thatgravyboat.jukebox.api.state.State

object Player {

    private val window = Window(version = ElementaVersion.V2)
    private var player: UIPlayer? = null

    private var isPlaying = false
    private var tempHide = false

    private fun checkAndInitPlayer() {
        if (player == null) {
            player = UIPlayer() childOf window
            
            // Ensure we're using the correct anchor point from config
            // First, try to read from config file directly to ensure we have the correct value
            var anchorOrdinalToUse: Int? = null
            val configFile = java.io.File("./config/craftify.toml")
            if (configFile.exists()) {
                try {
                    val content = configFile.readText()
                    val anchorPatterns = listOf(
                        Regex("anchor_point\\s*=\\s*(\\d+)"),
                        Regex("\"anchor_point\"\\s*=\\s*(\\d+)"),
                        Regex("'anchor_point'\\s*=\\s*(\\d+)"),
                        Regex("anchor_point\\s*=\\s*\"(\\d+)\""),
                        Regex("anchor_point\\s*=\\s*'(\\d+)'")
                    )
                    for (pattern in anchorPatterns) {
                        val match = pattern.find(content)
                        if (match != null) {
                            anchorOrdinalToUse = match.groupValues[1].toIntOrNull()
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors reading file
                }
            }
            
            // If we couldn't read from file or file doesn't exist, use the current config value
            if (anchorOrdinalToUse == null) {
                val currentAnchorOrdinal = try {
                    Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
                } catch (e: Exception) {
                    Config.anchorPointOrdinal
                }
                anchorOrdinalToUse = currentAnchorOrdinal
            }
            
            val correctAnchor = Anchor.values()[anchorOrdinalToUse.coerceIn(0, Anchor.values().size - 1)]
            // Ensure config is synced with the correct value
            if (Config.anchorPoint != correctAnchor || Config.anchorPointOrdinal != anchorOrdinalToUse) {
                // Update the ordinal field if needed
                if (Config.anchorPointOrdinal != anchorOrdinalToUse) {
                    try {
                        val field = Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }
                        field.setInt(Config, anchorOrdinalToUse)
                    } catch (e: Exception) {
                        Config.anchorPointOrdinal = anchorOrdinalToUse
                    }
                }
                Config.anchorPoint = correctAnchor
                Config.xOffset = correctAnchor.getDefaultXOffset()
                Config.yOffset = correctAnchor.getDefaultYOffset()
            }
            
            changePosition(correctAnchor)
            updateTheme()
            Initializer.getAPI()?.getState()?.let {
                player?.updateState(it)
            }
        }
    }

    fun isPlaying(): Boolean {
        return isPlaying
    }

    fun toggleHiding() {
        tempHide = !tempHide
    }

    fun updateTheme() {
        player?.updateTheme()
    }

    fun stopClient() {
        player?.clientStop()
    }

    fun changeSong() {
        AdManager.changeAd()
    }

    fun updatePlayer(state: State) {
        isPlaying = if (state.song.type.isAd()) {
            player?.updateState(AdManager.getAdState(state))
            false
        } else {
            player?.updateState(state)
            state.isPlaying
        }
    }

    fun changePosition(position: Anchor) {
        // Store the position so it's applied when player is created
        // Also update immediately if player already exists
        player?.apply {
            setX(position.getX(this@apply))
            setY(position.getY(this@apply))
        }
        // Update will be applied when checkAndInitPlayer() is called
    }

    fun onRender(matrix: UMatrixStack) {
        if (tempHide) return
        if (canRender() && Config.musicService != "disabled") {
            checkAndInitPlayer()
            ObsOverlayCompat.draw {
                window.draw(matrix)
            }
        }
    }

    private fun canRender(): Boolean {
        if (UScreen.currentScreen is PositionEditorScreen) return false
        val renderType = Config.renderType.canRender(UScreen.currentScreen)
        val displayMode = Config.displayMode.canDisplay(Initializer.getAPI()?.getState())
        return (UScreen.currentScreen is ScreenshotScreen || (renderType && displayMode)) && Config.musicService != "disabled"
    }

    // XY values taken from GuiScreen go there if anything screws up.
    fun onMouseClicked(button: Int): Boolean {
        if (Config.musicService == "disabled") return false
        if (tempHide) return false
        if (canRender() && player?.isHovered() == true) {
            player?.mouseClick(UMouse.Scaled.x, UMouse.Scaled.y, button)
            return true
        }
        return false
    }
}

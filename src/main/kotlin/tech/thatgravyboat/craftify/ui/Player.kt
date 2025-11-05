package tech.thatgravyboat.craftify.ui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import gg.essential.universal.UScreen
import org.lwjgl.input.Mouse
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
            var xOffsetFromFile: Float? = null
            var yOffsetFromFile: Float? = null
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
                    // Also read xOffset and yOffset
                    val xOffsetPattern = Regex("xOffset\\s*=\\s*([\\d.]+)")
                    val xOffsetMatch = xOffsetPattern.find(content)
                    if (xOffsetMatch != null) {
                        xOffsetFromFile = xOffsetMatch.groupValues[1].toFloatOrNull()
                    }
                    val yOffsetPattern = Regex("yOffset\\s*=\\s*([\\d.]+)")
                    val yOffsetMatch = yOffsetPattern.find(content)
                    if (yOffsetMatch != null) {
                        yOffsetFromFile = yOffsetMatch.groupValues[1].toFloatOrNull()
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
                // Use saved offsets if available, otherwise use defaults
                if (xOffsetFromFile != null) Config.xOffset = xOffsetFromFile
                else Config.xOffset = correctAnchor.getDefaultXOffset()
                if (yOffsetFromFile != null) Config.yOffset = yOffsetFromFile
                else Config.yOffset = correctAnchor.getDefaultYOffset()
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

    fun isEditMode(): Boolean {
        return player?.isEditMode == true
    }

    fun changePosition(position: Anchor) {
        // Don't change position if player is currently being dragged
        if (player?.isDragging == true) return
        
        // Also don't change position if we're in edit mode and have a saved position
        // (user has manually dragged it, so respect that position)
        if (player?.isEditMode == true) {
            try {
                val field = player?.javaClass?.getDeclaredField("savedPixelPosition")
                field?.isAccessible = true
                val savedPos = field?.get(player)
                if (savedPos != null) {
                    // User has dragged to a custom position, don't reset it
                    return
                }
            } catch (e: Exception) {
                // Field might not exist, continue
            }
        }
        
        // Clear saved pixel position when changing anchor point (use anchor-based positioning)
        player?.let { 
            // Use reflection to access private savedPixelPosition field
            try {
                val field = it::class.java.getDeclaredField("savedPixelPosition")
                field.isAccessible = true
                field.set(it, null)
            } catch (e: Exception) {
                // Field might not exist or be accessible, that's okay
            }
        }
        
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
            // Update drag during render for smooth 60+ FPS dragging
            player?.updateDrag()
            // Update progress bar drag (for seeking)
            player?.progress?.updateDrag()
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
    
    // Handle mouse drag - called every tick to update drag position
    fun onMouseDrag() {
        if (Config.musicService == "disabled") return
        if (tempHide) return
        if (canRender() && player != null) {
            player?.updateDrag()
        }
    }
}

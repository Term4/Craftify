package tech.thatgravyboat.craftify.ui

import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.universal.UScreen
import gg.essential.universal.UMouse
import gg.essential.universal.UResolution
import gg.essential.vigilance.gui.VigilancePalette
import org.lwjgl.input.Mouse
import tech.thatgravyboat.craftify.config.Config
import tech.thatgravyboat.craftify.platform.runOnMcThread
import tech.thatgravyboat.craftify.themes.ThemeConfig
import tech.thatgravyboat.craftify.ui.constraints.ConfigColorConstraint
import tech.thatgravyboat.craftify.ui.constraints.ThemeFontProvider
import tech.thatgravyboat.craftify.ui.enums.Anchor
import tech.thatgravyboat.craftify.utils.MemoryImageCache
import tech.thatgravyboat.craftify.utils.RenderUtils
import tech.thatgravyboat.craftify.utils.Utils.clearFormatting
import tech.thatgravyboat.jukebox.api.state.State
import java.net.URL
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class UIPlayer : UIRoundedRectangle(0f) {

    // Helper function to get scaled pixel value
    private fun Float.scaledPixel() = (this * Config.hudScale).pixel()
    
    // Drag state
    private var dragStartMousePos: Pair<Float, Float>? = null // Initial mouse screen position when drag started
    private var dragStartComponentPos: Pair<Float, Float>? = null // Initial component screen position when drag started
    var isDragging = false // Made public so Player can check if dragging
    var isEditMode = false // Edit mode - when true, dragging is enabled; when false, controls work normally

    init {
        // Read anchor point from config file BEFORE calling updateDimensions()
        // This ensures we set the correct position from the start
        val configFile = java.io.File("./config/craftify.toml")
        var anchorOrdinalToUse: Int? = null
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
                // Ignore errors
            }
        }
        
        // If couldn't read from file, use config object
        if (anchorOrdinalToUse == null) {
            anchorOrdinalToUse = try {
                Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.anchorPointOrdinal
            }
        }
        
        val anchorToUse = Anchor.values()[(anchorOrdinalToUse ?: 0).coerceIn(0, Anchor.values().size - 1)]
        
        // Update dimensions AND set position based on anchor point in one call
        constrain {
            height = 50f.scaledPixel()
            width = 150f.scaledPixel()
            // Set position based on anchor point, not 0.percent()
            x = anchorToUse.getX(this@UIPlayer)
            y = anchorToUse.getY(this@UIPlayer)
            color = ConfigColorConstraint("background")
            radius = ThemeConfig.backgroundRadius.pixels()
        }
        
        onMouseEnter {
            // Show different border color when in edit mode
            val borderColor = if (isEditMode) {
                java.awt.Color(255, 255, 0, 255) // Yellow border to indicate edit mode
            } else {
                ThemeConfig.borderColor
            }
            enableEffect(OutlineEffect(borderColor, if (isEditMode) 2F else 1F, drawInsideChildren = true))
            if (Config.premiumControl) {
                setHeight(63f.scaledPixel())
                if (anchorToUse.ordinal > 4) setY(anchorToUse.getY(this) - 13f.scaledPixel())
                this.addChild(controls)
            }
        }

        onMouseLeave {
            removeEffect<OutlineEffect>()
            setHeight(50f.scaledPixel())
            if (anchorToUse.ordinal > 4) setY(anchorToUse.getY(this))
            this.removeChild(controls)
        }
        
        // Update border color when edit mode changes
        // This ensures the yellow border shows when edit mode is active
        
        // Drag functionality - click and drag to move the display (only in edit mode)
        onMouseClick { event ->
            if (event.mouseButton == 0 && isEditMode) { // Left mouse button, only in edit mode
                // Don't start drag if clicking on controls (they're at y=50, so check if click is in that area)
                val clickY = event.relativeY
                val controlsArea = 50f * Config.hudScale
                if (clickY >= controlsArea) {
                    // Click is in controls area, don't start drag
                    return@onMouseClick
                }
                // Store initial mouse position (screen coordinates)
                dragStartMousePos = UMouse.Scaled.x.toFloat() to UMouse.Scaled.y.toFloat()
                // Store the initial component position (screen coordinates)
                dragStartComponentPos = getLeft() to getTop()
                isDragging = true
            }
        }
        
        onMouseRelease {
            if (isDragging) {
                // Save position when drag ends
                saveDragPosition()
                dragStartMousePos = null
                dragStartComponentPos = null
                isDragging = false
            }
        }
    }

    private var imageUrl = ""

    private val image by UIBlock(VigilancePalette.getHighlight()).constrain {
        height = 40f.scaledPixel()
        width = 40f.scaledPixel()
        x = 5f.scaledPixel()
        y = 5f.scaledPixel()
    } childOf this

    private val info by UIContainer().constrain {
        width = 95f.scaledPixel()
        height = 40f.scaledPixel()
        x = 50f.scaledPixel()
        y = 5f.scaledPixel()
    } childOf this

    private val title by UITextMarquee(text = "Song Title").constrain {
        width = 100.percent()
        height = 10f.scaledPixel()
        textScale = (0.75f * Config.hudScale).pixel()
        color = ConfigColorConstraint("title")
        fontProvider = ThemeFontProvider("title")
    } childOf info

    private val artist by lazy { UIWrappedText("Artists, here").constrain {
        width = 100.percent()
        height = 10f.scaledPixel()
        y = 12f.scaledPixel()
        textScale = (0.5f * Config.hudScale).pixel()
        color = ConfigColorConstraint("artist")
        fontProvider = ThemeFontProvider("artist")
    } childOf info }

    private val progress by UIProgressBar().constrain {
        width = 100.percent()
        height = 3f.scaledPixel()
        y = (40f - 3f).scaledPixel()
    } childOf info

    private val controls by UIControls(
        onEditModeToggle = { isEditMode = !isEditMode },
        getEditModeState = { isEditMode }
    ).constrain {
        width = ChildBasedSizeConstraint()
        height = 10f.scaledPixel()
        y = 50f.scaledPixel()
        x = CenterConstraint()
    }
    
    private fun updateDimensions() {
        // Don't update position if we're currently dragging - this prevents flashing/disappearing
        if (isDragging) {
            // Only update dimensions, not position
            constrain {
                height = 50f.scaledPixel()
                width = 150f.scaledPixel()
                color = ConfigColorConstraint("background")
                radius = ThemeConfig.backgroundRadius.pixels()
            }
            return
        }
        
        // Read anchor point from config to preserve position
        val configFile = java.io.File("./config/craftify.toml")
        var anchorOrdinalToUse: Int? = null
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
                // Ignore errors
            }
        }
        
        // If couldn't read from file, use config object
        if (anchorOrdinalToUse == null) {
            anchorOrdinalToUse = try {
                Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.anchorPointOrdinal
            }
        }
        
        val anchorToUse = Anchor.values()[(anchorOrdinalToUse ?: 0).coerceIn(0, Anchor.values().size - 1)]
        
        constrain {
            height = 50f.scaledPixel()
            width = 150f.scaledPixel()
            // Preserve anchor point position, don't reset to 0.percent()
            x = anchorToUse.getX(this@UIPlayer)
            y = anchorToUse.getY(this@UIPlayer)
            color = ConfigColorConstraint("background")
            radius = ThemeConfig.backgroundRadius.pixels()
        }
    }

    fun clientStop() {
        progress.tempStop()
    }

    fun updateState(state: State) {
        runOnMcThread {
            progress.updateTime(state.songState.progress, state.songState.duration)
            val artistText = state.song.artists.subList(0, state.song.artists.size.coerceAtMost(3)).joinToString(", ")
            artist.setText(artistText.clearFormatting())
            title.updateText(state.song.title)
            controls.updateState(state)

            try {
                if (imageUrl != state.song.cover && state.song.cover.isNotEmpty()) {
                    imageUrl = state.song.cover
                    synchronized(image.children) {
                        val url = URL(state.song.cover)
                        image.clearChildren()
                        image.addChild(
                            UIImage(CompletableFuture.supplyAsync {
                                MemoryImageCache.COVER_IMAGE.getOrSet(url) { url ->
                                    RenderUtils.getImage(url).let {
                                        val targetSize = (40 * Config.hudScale).toInt()
                                        val scale = min(it.width / targetSize, it.height / targetSize)
                                        val extractSize = targetSize * scale
                                        it.getSubimage(it.width / 2 - extractSize / 2, it.height / 2 - extractSize / 2, extractSize, extractSize)
                                    }
                                }
                            }, EmptyImageProvider, EmptyImageProvider).constrain {
                                width = 100.percent()
                                height = 100.percent()
                            }
                        )
                    }
                }
            } catch (ignored: Exception) {
                // Ignoring exception due that it would be that Spotify sent a broken url.
            }
        }
    }

    fun updateTheme() {
        updateDimensions()
        // Update background color directly - force recalculation by creating new constraint
        val bgConstraint = ConfigColorConstraint("background")
        bgConstraint.recalculate = true
        constrain {
            color = bgConstraint
        }
        // Update border if currently hovered
        if (isHovered()) {
            val borderColor = if (isEditMode) {
                java.awt.Color(255, 255, 0, 255) // Yellow border to indicate edit mode
            } else {
                ThemeConfig.borderColor
            }
            removeEffect<OutlineEffect>()
            enableEffect(OutlineEffect(borderColor, if (isEditMode) 2F else 1F, drawInsideChildren = true))
        }
        progress.updateTheme()
        controls.updateTheme()
        if (ThemeConfig.hideImage) {
            this.removeChild(image)
            info.setX(5f.scaledPixel())
            this.setWidth(105f.scaledPixel())
        } else {
            this.addChild(image)
            info.setX(50f.scaledPixel())
            this.setWidth(150f.scaledPixel())
        }
        // Update image and info dimensions
        image.setWidth(40f.scaledPixel())
        image.setHeight(40f.scaledPixel())
        image.setX(5f.scaledPixel())
        image.setY(5f.scaledPixel())
        info.setWidth(95f.scaledPixel())
        info.setHeight(40f.scaledPixel())
        info.setY(5f.scaledPixel())
        title.setHeight(10f.scaledPixel())
        title.setTextScale((0.75f * Config.hudScale).pixel())
        artist.setHeight(10f.scaledPixel())
        artist.setY(12f.scaledPixel())
        artist.setTextScale((0.5f * Config.hudScale).pixel())
        progress.setHeight(3f.scaledPixel())
        progress.setY((40f - 3f).scaledPixel())
        controls.setHeight(10f.scaledPixel())
        controls.setY(50f.scaledPixel())
        this.setRadius(ThemeConfig.backgroundRadius.pixels())
    }

    override fun isHovered(): Boolean {
        // Allow hover detection even when no screen is open (for dragging)
        return super.isHovered()
    }
    
    // Called every tick to handle dragging
    fun updateDrag() {
        if (!isDragging || dragStartMousePos == null || dragStartComponentPos == null) return
        
        if (Mouse.isButtonDown(0)) {
            val currentMouseX = UMouse.Scaled.x.toFloat()
            val currentMouseY = UMouse.Scaled.y.toFloat()
            
            // Calculate how far the mouse has moved from the initial click position
            val deltaX = currentMouseX - dragStartMousePos!!.first
            val deltaY = currentMouseY - dragStartMousePos!!.second
            
            // Calculate new component position: initial position + mouse movement
            val newX = dragStartComponentPos!!.first + deltaX
            val newY = dragStartComponentPos!!.second + deltaY
            
            val currentWidth = if (ThemeConfig.hideImage) 105f * Config.hudScale else 150f * Config.hudScale
            val currentHeight = 50f * Config.hudScale
            
            // Use setX/setY instead of constrain to avoid conflicts
            val clampedX = newX.coerceIn(0f, UResolution.scaledWidth - currentWidth)
            val clampedY = newY.coerceIn(0f, UResolution.scaledHeight - currentHeight)
            
            setX(clampedX.pixels())
            setY(clampedY.pixels())
        } else {
            // Mouse button released, stop dragging first
            isDragging = false
            // Then save position (this will not trigger changePosition since isDragging is now false)
            saveDragPosition()
            dragStartMousePos = null
            dragStartComponentPos = null
        }
    }
    
    private fun saveDragPosition() {
        // Don't save if still dragging
        if (isDragging) return
        
        // Calculate which anchor point this position is closest to
        val centerX = getLeft() + getWidth() / 2
        val centerY = getTop() + getHeight() / 2
        
        // Determine which anchor point based on position
        val xPercent = centerX / UResolution.scaledWidth
        val yPercent = centerY / UResolution.scaledHeight
        
        // Store old values to check if anything changed
        val oldXOffset = Config.xOffset
        val oldYOffset = Config.yOffset
        val oldAnchorOrdinal = Config.anchorPointOrdinal
        
        // Update xOffset and yOffset based on current position
        Config.xOffset = xPercent
        Config.yOffset = yPercent
        
        // Update anchor point to match the closest one
        val closestAnchor = when {
            xPercent < 0.33f && yPercent < 0.33f -> Anchor.TOP_LEFT
            xPercent < 0.67f && yPercent < 0.33f -> Anchor.TOP_MIDDLE
            yPercent < 0.33f -> Anchor.TOP_RIGHT
            xPercent < 0.33f && yPercent < 0.67f -> Anchor.MIDDLE_LEFT
            xPercent >= 0.67f && yPercent < 0.67f -> Anchor.MIDDLE_RIGHT
            xPercent < 0.33f -> Anchor.BOTTOM_LEFT
            xPercent < 0.67f -> Anchor.BOTTOM_MIDDLE
            else -> Anchor.BOTTOM_RIGHT
        }
        
        Config.anchorPointOrdinal = closestAnchor.ordinal
        Config.anchorPoint = closestAnchor
        
        // Only save if position actually changed to avoid unnecessary updates and position resets
        if (oldAnchorOrdinal != closestAnchor.ordinal || 
            kotlin.math.abs(oldXOffset - xPercent) > 0.01f ||
            kotlin.math.abs(oldYOffset - yPercent) > 0.01f) {
            Config.markDirty()
            Config.writeData()
        }
    }
}

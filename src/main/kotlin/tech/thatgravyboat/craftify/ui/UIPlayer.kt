package tech.thatgravyboat.craftify.ui

import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.universal.UScreen
import gg.essential.vigilance.gui.VigilancePalette
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
            enableEffect(OutlineEffect(ThemeConfig.borderColor, 1F, drawInsideChildren = true))
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

    private val controls by UIControls().constrain {
        width = ChildBasedSizeConstraint()
        height = 10f.scaledPixel()
        y = 50f.scaledPixel()
        x = CenterConstraint()
    }
    
    private fun updateDimensions() {
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
        return UScreen.currentScreen != null && super.isHovered()
    }
}

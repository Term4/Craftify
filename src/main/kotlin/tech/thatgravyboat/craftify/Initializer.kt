package tech.thatgravyboat.craftify

import gg.essential.universal.UChat
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UScreen
import gg.essential.universal.utils.MCScreen
import gg.essential.universal.wrappers.UPlayer
import gg.essential.vigilance.gui.SettingsGui
import tech.thatgravyboat.craftify.config.Config
import tech.thatgravyboat.craftify.platform.*
import tech.thatgravyboat.craftify.ui.enums.Anchor
import tech.thatgravyboat.craftify.ui.enums.DisplayMode
import tech.thatgravyboat.craftify.ui.enums.LinkingMode
import tech.thatgravyboat.craftify.ui.enums.RenderType
import tech.thatgravyboat.craftify.services.ServiceHelper.close
import tech.thatgravyboat.craftify.services.ServiceHelper.setup
import tech.thatgravyboat.craftify.services.ServiceType
import tech.thatgravyboat.craftify.services.ads.AdManager
import tech.thatgravyboat.craftify.services.config.ServiceConfig
import tech.thatgravyboat.craftify.services.update.Updater
import tech.thatgravyboat.craftify.ssl.FixSSL
import tech.thatgravyboat.craftify.ui.Player
import tech.thatgravyboat.craftify.utils.Utils
import tech.thatgravyboat.jukebox.api.service.BaseService
import tech.thatgravyboat.jukebox.api.service.Service

object Initializer {

    private val skipForward = UKeybind("Skip Forward", "Craftify", UKeybind.Type.KEYBOARD, UKeyboard.KEY_NONE)
    private val skipPrevious = UKeybind("Skip Previous", "Craftify", UKeybind.Type.KEYBOARD, UKeyboard.KEY_NONE)
    private val togglePlaying = UKeybind("Toggle Playing", "Craftify", UKeybind.Type.KEYBOARD, UKeyboard.KEY_NONE)
    private val hidePlayer = UKeybind("Toggle Craftify HUD", "Craftify", UKeybind.Type.KEYBOARD, UKeyboard.KEY_NONE)

    private var inited = false
    private var tickCount = 0

    private var api: BaseService? = null
    
    // Track last known config values to detect OneConfig changes
    private var lastAnchorPointOrdinal = -1
    private var lastHudScale = -1f
    private var lastLinkModeOrdinal = -1
    private var lastRenderTypeOrdinal = -1
    private var lastDisplayModeOrdinal = -1

    fun init() {

        //#if MODERN==0
        FixSSL.fixup()
        Utils.setupJukeboxHttp()
        //#endif
        Updater.check()
        AdManager.load()

        Events.TICK.register { 
            onTick()
        }
        Events.RENDER.register { onRender(it) }
        Events.MOUSE_CLICKED.register { onMouseClicked(it) }
        Events.SCREEN_CHANGED.register { onScreenChanged(it) }

        //#if MODERN==0
        tech.thatgravyboat.cosmetics.Cosmetics.initialize()
        //#endif
        // Load service configs (Spotify tokens, etc.) before creating services
        ServiceConfig.load()
        reloadService()

        skipForward.register()
        skipPrevious.register()
        togglePlaying.register()
        hidePlayer.register()
        EventHandler
    }

    private fun onTick() {
        if (!inited) {
            registerCommand(Command.command, Command.commands)
            // Initialize last known values
            lastAnchorPointOrdinal = Config.anchorPointOrdinal
            lastHudScale = Config.hudScale
            lastLinkModeOrdinal = Config.linkModeOrdinal
            lastRenderTypeOrdinal = Config.renderTypeOrdinal
            lastDisplayModeOrdinal = Config.displayModeOrdinal
            inited = true
        }
        
        // Continuously monitor and restore values from config file if they're reset
        // This handles cases where OneConfig/Vigilant resets values after init
        val configFile = java.io.File("./config/craftify.toml")
        if (configFile.exists() && (tickCount % 20 == 0)) { // Check every second (20 ticks)
            try {
                val content = configFile.readText()
                var needsRestore = false
                
                // Parse values from file
                val anchorMatch = Regex("anchor_point\\s*=\\s*(\\d+)").find(content)
                val scaleMatch = Regex("hud_scale\\s*=\\s*([\\d.]+)").find(content)
                val linkMatch = Regex("link_mode\\s*=\\s*(\\d+)").find(content)
                val renderMatch = Regex("render_type\\s*=\\s*(\\d+)").find(content)
                val displayMatch = Regex("display_mode\\s*=\\s*(\\d+)").find(content)
                
                val fileAnchor = anchorMatch?.groupValues?.get(1)?.toIntOrNull()
                val fileScale = scaleMatch?.groupValues?.get(1)?.toFloatOrNull()
                val fileLink = linkMatch?.groupValues?.get(1)?.toIntOrNull()
                val fileRender = renderMatch?.groupValues?.get(1)?.toIntOrNull()
                val fileDisplay = displayMatch?.groupValues?.get(1)?.toIntOrNull()
                
                // Check current values using reflection
                val currentAnchor = try {
                    Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
                } catch (e: Exception) { Config.anchorPointOrdinal }
                val currentScale = try {
                    Config::class.java.getDeclaredField("hudScale").apply { isAccessible = true }.getFloat(Config)
                } catch (e: Exception) { Config.hudScale }
                val currentLink = try {
                    Config::class.java.getDeclaredField("linkModeOrdinal").apply { isAccessible = true }.getInt(Config)
                } catch (e: Exception) { Config.linkModeOrdinal }
                val currentRender = try {
                    Config::class.java.getDeclaredField("renderTypeOrdinal").apply { isAccessible = true }.getInt(Config)
                } catch (e: Exception) { Config.renderTypeOrdinal }
                val currentDisplay = try {
                    Config::class.java.getDeclaredField("displayModeOrdinal").apply { isAccessible = true }.getInt(Config)
                } catch (e: Exception) { Config.displayModeOrdinal }
                
                // Restore if file has different values (but only if current value is default and file value is not)
                // This prevents overwriting user changes with file values, but restores if reset to defaults
                if (fileAnchor != null && fileAnchor >= 0 && currentAnchor != fileAnchor) {
                    // Only restore if current is default (0) and file is not, OR if both are different non-defaults (file wins)
                    if ((currentAnchor == 0 && fileAnchor != 0) || (currentAnchor != fileAnchor && currentAnchor != 0 && fileAnchor != 0)) {
                        try {
                            Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.setInt(Config, fileAnchor)
                            lastAnchorPointOrdinal = fileAnchor
                            needsRestore = true
                        } catch (e: Exception) {
                            Config.anchorPointOrdinal = fileAnchor
                            lastAnchorPointOrdinal = fileAnchor
                            needsRestore = true
                        }
                    }
                }
                if (fileScale != null && fileScale >= 0.1f && kotlin.math.abs(currentScale - fileScale) > 0.001f) {
                    // Only restore if current is default (0 or 1.0) and file is not, OR if both are different non-defaults
                    val isCurrentDefault = kotlin.math.abs(currentScale) < 0.001f || kotlin.math.abs(currentScale - 1.0f) < 0.001f
                    val isFileDefault = kotlin.math.abs(fileScale - 1.0f) < 0.001f
                    if ((isCurrentDefault && !isFileDefault) || (!isCurrentDefault && !isFileDefault && kotlin.math.abs(currentScale - fileScale) > 0.001f)) {
                        try {
                            Config::class.java.getDeclaredField("hudScale").apply { isAccessible = true }.setFloat(Config, fileScale)
                            lastHudScale = fileScale
                            needsRestore = true
                        } catch (e: Exception) {
                            Config.hudScale = fileScale
                            lastHudScale = fileScale
                            needsRestore = true
                        }
                    }
                }
                if (fileLink != null && fileLink >= 0 && currentLink != fileLink) {
                    if ((currentLink == 0 && fileLink != 0) || (currentLink != fileLink && currentLink != 0 && fileLink != 0)) {
                        try {
                            Config::class.java.getDeclaredField("linkModeOrdinal").apply { isAccessible = true }.setInt(Config, fileLink)
                            lastLinkModeOrdinal = fileLink
                            needsRestore = true
                        } catch (e: Exception) {
                            Config.linkModeOrdinal = fileLink
                            lastLinkModeOrdinal = fileLink
                            needsRestore = true
                        }
                    }
                }
                if (fileRender != null && fileRender >= 0 && currentRender != fileRender) {
                    if ((currentRender == 0 && fileRender != 0) || (currentRender != fileRender && currentRender != 0 && fileRender != 0)) {
                        try {
                            Config::class.java.getDeclaredField("renderTypeOrdinal").apply { isAccessible = true }.setInt(Config, fileRender)
                            lastRenderTypeOrdinal = fileRender
                            needsRestore = true
                        } catch (e: Exception) {
                            Config.renderTypeOrdinal = fileRender
                            lastRenderTypeOrdinal = fileRender
                            needsRestore = true
                        }
                    }
                }
                if (fileDisplay != null && fileDisplay >= 0 && currentDisplay != fileDisplay) {
                    if ((currentDisplay == 0 && fileDisplay != 0) || (currentDisplay != fileDisplay && currentDisplay != 0 && fileDisplay != 0)) {
                        try {
                            Config::class.java.getDeclaredField("displayModeOrdinal").apply { isAccessible = true }.setInt(Config, fileDisplay)
                            lastDisplayModeOrdinal = fileDisplay
                            needsRestore = true
                        } catch (e: Exception) {
                            Config.displayModeOrdinal = fileDisplay
                            lastDisplayModeOrdinal = fileDisplay
                            needsRestore = true
                        }
                    }
                }
                
                if (needsRestore) {
                    // Sync enums and update UI
                    Config.anchorPoint = Anchor.values()[Config.anchorPointOrdinal.coerceIn(0, Anchor.values().size - 1)]
                    Config.xOffset = Config.anchorPoint.getDefaultXOffset()
                    Config.yOffset = Config.anchorPoint.getDefaultYOffset()
                    Config.linkMode = LinkingMode.values()[Config.linkModeOrdinal.coerceIn(0, LinkingMode.values().size - 1)]
                    Config.renderType = RenderType.values()[Config.renderTypeOrdinal.coerceIn(0, RenderType.values().size - 1)]
                    Config.displayMode = DisplayMode.values()[Config.displayModeOrdinal.coerceIn(0, DisplayMode.values().size - 1)]
                    Player.changePosition(Config.anchorPoint)
                    Player.updateTheme()
                    Config.markDirty()
                    Config.writeData()
                }
            } catch (e: Exception) {
                // Ignore errors reading config file
            }
        }
        
        tickCount++
        
        // Detect when OneConfig changes values directly (bypassing setters/callbacks)
        // Check every tick to catch changes immediately
        // Use java reflection to read the actual field value, bypassing Kotlin property accessors
        val currentAnchorOrdinal = try {
            Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
        } catch (e: Exception) {
            Config.anchorPointOrdinal
        }
        
        if (lastAnchorPointOrdinal != currentAnchorOrdinal) {
            lastAnchorPointOrdinal = currentAnchorOrdinal
            // Directly sync enum from ordinal value
            val newAnchor = Anchor.values()[currentAnchorOrdinal.coerceIn(0, Anchor.values().size - 1)]
            Config.anchorPoint = newAnchor
            Config.xOffset = newAnchor.getDefaultXOffset()
            Config.yOffset = newAnchor.getDefaultYOffset()
            Player.changePosition(newAnchor)
            Config.markDirty()
            Config.writeData()
        }
        
        val currentHudScale = try {
            Config::class.java.getDeclaredField("hudScale").apply { isAccessible = true }.getFloat(Config)
        } catch (e: Exception) {
            Config.hudScale
        }
        
        if (kotlin.math.abs(lastHudScale - currentHudScale) > 0.001f) {
            lastHudScale = currentHudScale
            val clampedScale = currentHudScale.coerceIn(0.1f, 5.0f)
            try {
                Config::class.java.getDeclaredField("hudScale").apply { isAccessible = true }.setFloat(Config, clampedScale)
            } catch (e: Exception) {
                Config.hudScale = clampedScale
            }
            Player.updateTheme()
            Config.markDirty()
            Config.writeData()
        }
        
        val currentLinkOrdinal = try {
            Config::class.java.getDeclaredField("linkModeOrdinal").apply { isAccessible = true }.getInt(Config)
        } catch (e: Exception) {
            Config.linkModeOrdinal
        }
        
        if (lastLinkModeOrdinal != currentLinkOrdinal) {
            lastLinkModeOrdinal = currentLinkOrdinal
            Config.linkMode = LinkingMode.values()[currentLinkOrdinal.coerceIn(0, LinkingMode.values().size - 1)]
            Config.markDirty()
            Config.writeData()
        }
        
        val currentRenderOrdinal = try {
            Config::class.java.getDeclaredField("renderTypeOrdinal").apply { isAccessible = true }.getInt(Config)
        } catch (e: Exception) {
            Config.renderTypeOrdinal
        }
        
        if (lastRenderTypeOrdinal != currentRenderOrdinal) {
            lastRenderTypeOrdinal = currentRenderOrdinal
            Config.renderType = RenderType.values()[currentRenderOrdinal.coerceIn(0, RenderType.values().size - 1)]
            Config.markDirty()
            Config.writeData()
        }
        
        val currentDisplayOrdinal = try {
            Config::class.java.getDeclaredField("displayModeOrdinal").apply { isAccessible = true }.getInt(Config)
        } catch (e: Exception) {
            Config.displayModeOrdinal
        }
        
        if (lastDisplayModeOrdinal != currentDisplayOrdinal) {
            lastDisplayModeOrdinal = currentDisplayOrdinal
            Config.displayMode = DisplayMode.values()[currentDisplayOrdinal.coerceIn(0, DisplayMode.values().size - 1)]
            Config.markDirty()
            Config.writeData()
        }
        
        if (Config.firstTime && UPlayer.hasPlayer()) {
            Config.firstTime = false
            Config.markDirty()
            Config.writeData()

            UChat.chat("")
            UChat.chat("\u00A77-------[\u00A7aCraftify\u00A77]-------")
            UChat.chat("\u00A76This is your first time loading the mod.")
            UChat.chat("\u00A76To setup the mod run \u00A79/craftify\u00A76 and go to the Login category.")
            UChat.chat("\u00A76If you would like to support the creator you can")
            UChat.chat("\u00A76sub to \u00A72ThatGravyBoat\u00A76 on \u00A7cpatreon\u00A76, link in the")
            UChat.chat("\u00A76config you will also get a small cosmetic if you do.")
            UChat.chat("\u00A77----------------------")
            UChat.chat("")
        }
        if (Updater.hasUpdate() && UPlayer.hasPlayer()) {
            Updater.showMessage()
        }
        if (isPressed(skipForward)) {
            Utils.async {
                api?.move(true)
            }
        }
        if (isPressed(skipPrevious)) {
            Utils.async {
                api?.move(false)
            }
        }
        if (isPressed(togglePlaying)) {
            Utils.async {
                api?.setPaused(Player.isPlaying())
                Player.stopClient()
            }
        }
        if (isPressed(hidePlayer)) {
            Player.toggleHiding()
        }
        Utils.getOpenScreen()?.let {
            UScreen.displayScreen(it)
        }
    }

    private fun onRender(matrix: UMatrixStack) {
        if (isGuiHidden()) return
        Player.onRender(matrix)
    }

    private fun onMouseClicked(button: Int): Boolean {
        return Player.onMouseClicked(button)
    }

    private fun onScreenChanged(screen: MCScreen?) {
        if (screen == null && UScreen.currentScreen is SettingsGui) {
            // Config screen just closed - read actual field values and sync enums
            // Use reflection to get the actual values OneConfig/Vigilant might have set
            val currentAnchorOrdinal = try {
                Config::class.java.getDeclaredField("anchorPointOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.anchorPointOrdinal
            }
            val currentLinkOrdinal = try {
                Config::class.java.getDeclaredField("linkModeOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.linkModeOrdinal
            }
            val currentRenderOrdinal = try {
                Config::class.java.getDeclaredField("renderTypeOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.renderTypeOrdinal
            }
            val currentDisplayOrdinal = try {
                Config::class.java.getDeclaredField("displayModeOrdinal").apply { isAccessible = true }.getInt(Config)
            } catch (e: Exception) {
                Config.displayModeOrdinal
            }
            val currentScale = try {
                Config::class.java.getDeclaredField("hudScale").apply { isAccessible = true }.getFloat(Config)
            } catch (e: Exception) {
                Config.hudScale
            }
            
            // Sync enums from ordinals
            Config.linkMode = LinkingMode.values()[currentLinkOrdinal.coerceIn(0, LinkingMode.values().size - 1)]
            Config.anchorPoint = Anchor.values()[currentAnchorOrdinal.coerceIn(0, Anchor.values().size - 1)]
            Config.renderType = RenderType.values()[currentRenderOrdinal.coerceIn(0, RenderType.values().size - 1)]
            Config.displayMode = DisplayMode.values()[currentDisplayOrdinal.coerceIn(0, DisplayMode.values().size - 1)]
            Config.hudScale = currentScale.coerceIn(0.1f, 5.0f)
            
            // Update offsets for anchor point
            Config.xOffset = Config.anchorPoint.getDefaultXOffset()
            Config.yOffset = Config.anchorPoint.getDefaultYOffset()
            
            // Force save multiple times to ensure persistence
            Config.markDirty()
            Config.writeData()
            // Save again after a brief delay to catch any OneConfig saves
            Utils.async {
                Thread.sleep(100)
                Config.markDirty()
                Config.writeData()
            }
            
            // Apply config changes
            Player.changePosition(Config.anchorPoint)
            Player.updateTheme()
            
            val service = Config.musicService
            if (service == "disabled") {
                api?.stop()
                api?.close()
                api = null
            } else {
                reloadService()
            }
        }
    }

    fun reloadService() {
        val service = Config.musicService.takeIf { it != "disabled" } ?: return
        val type = ServiceType.fromId(service) ?: return

        api?.stop()
        api?.close()
        api = type.create()
        api?.start()
        api?.setup()
    }

    fun getAPI(): Service? {
        return api
    }
}
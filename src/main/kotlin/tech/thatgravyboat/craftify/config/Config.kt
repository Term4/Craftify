package tech.thatgravyboat.craftify.config

import gg.essential.universal.UScreen
import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.Property
import gg.essential.vigilance.data.PropertyType
import tech.thatgravyboat.craftify.screens.servers.ServersScreen
import tech.thatgravyboat.craftify.ui.Player
import tech.thatgravyboat.craftify.ui.enums.Anchor
import tech.thatgravyboat.craftify.ui.enums.DisplayMode
import tech.thatgravyboat.craftify.ui.enums.LinkingMode
import tech.thatgravyboat.craftify.ui.enums.RenderType
import tech.thatgravyboat.craftify.utils.*
import java.io.File

object Config : Vigilant(File("./config/craftify.toml")) {

    override val migrations = ConfigMigrations.migrations

    // General
    var firstTime = true
    var musicService: String? = "disabled"
    // Enum fields are for internal use, ordinal properties are saved/loaded by Vigilant
    // Not registered in category builder, so Vigilant won't save/load them
    var linkMode = LinkingMode.OPEN
    // Don't initialize with default - let Vigilant load it from config
    var linkModeOrdinal: Int = 0
        get() = field
        set(value) {
            field = value.coerceIn(0, LinkingMode.values().size - 1)
            linkMode = LinkingMode.values()[field]
        }
    var announcementEnabled = false
    var announcementMessage = "&aCraftify > &7Now Playing: &b\${song} by \${artists}"

    // Server Stuff
    var sendPackets = false
    var allowedServers = ""

    // Rendering
    // Enum fields are for internal use, ordinal properties are saved/loaded by Vigilant
    // Not registered in category builder, so Vigilant won't save/load them
    var anchorPoint = Anchor.TOP_LEFT
    // Don't initialize with default - let Vigilant load it from config
    var anchorPointOrdinal: Int = 0
        get() = field
        set(value) {
            field = value.coerceIn(0, Anchor.values().size - 1)
            anchorPoint = Anchor.values()[field]
        }
    
    // Enum fields are for internal use, ordinal properties are saved/loaded by Vigilant
    // Not registered in category builder, so Vigilant won't save/load them
    var renderType = RenderType.NON_INTRUSIVE
    // Don't initialize with default - let Vigilant load it from config
    var renderTypeOrdinal: Int = 0
        get() = field
        set(value) {
            field = value.coerceIn(0, RenderType.values().size - 1)
            renderType = RenderType.values()[field]
        }
    
    // Enum fields are for internal use, ordinal properties are saved/loaded by Vigilant
    // Not registered in category builder, so Vigilant won't save/load them
    var displayMode = DisplayMode.WHEN_SONG_FOUND
    // Don't initialize with default - let Vigilant load it from config
    var displayModeOrdinal: Int = 0
        get() = field
        set(value) {
            field = value.coerceIn(0, DisplayMode.values().size - 1)
            displayMode = DisplayMode.values()[field]
        }
    var premiumControl = false
    var streamerMode = false
    var xOffset = 0f
    var yOffset = 0f
    
    @Property(
        type = PropertyType.DECIMAL_SLIDER,
        name = "HUD Scale",
        category = "Rendering",
        description = "Scale factor for the HUD overlay. 1.0 = normal size, 0.5 = half size, 2.0 = double size. Recommended range: 0.5 to 3.0.",
        minF = 0.1f,
        maxF = 5.0f,
        decimalPlaces = 2
    )
    var hudScale: Float = 1.0f
        set(value) {
            // Clamp to valid range
            field = value.coerceIn(0.1f, 5.0f)
        }

    init {
        category("General") {
            switch(::firstTime, "First Time", hidden = true)

            prop<ServiceProperty>(::musicService, "Music Service", "What service you want to use for fetching the music you're listening to?")
            selector(::linkModeOrdinal, "Link Mode", "How you will get/display the link when you click on the link button.", LinkingMode.values().map { it.toString() }) {
                // Setter syncs enum automatically
                markDirty()
                writeData()
            }
            boolean(::announcementEnabled, "Announcement", "If you want your new song to be announced into chat.")
            string(::announcementMessage, "Announcement Message", """
                Format for the announcement message (chat message). 
                Variables:
                - ${'$'}{song} will be replaced with the song name.
                - ${'$'}{artists} will be replaced by the artists.
                - ${'$'}{artist} will be replaced by the first artist.
            """.trimIndent())
            button("Theme Config", "Open theme config.", "Open") {
                gui()?.let(UScreen::displayScreen)
            }

            subcategory("Self Promotion") {
                button("Discord", "", "Visit") { Utils.openUrl("https://discord.thatgravyboat.tech") }
                button("Patreon", "", "Visit") { Utils.openUrl("https://patreon.com/thatgravyboat") }
                button("Twitter", "", "Visit") { Utils.openUrl("https://twitter.com/ThatGravyBoat") }
                button("YouTube", "", "Visit") { Utils.openUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
            }
        }

        category("Servers") {
            text(::allowedServers, "Allowed Servers", hidden = true)

            boolean(::sendPackets, "Send Songs To Server", "Send the song you're listening to the allowed server you are currently on when it changes.")
            button("Shared Servers", "The server that you allow automatic sharing of your current song to.\nThis allows other mods to get your current song to display it, should not be turned on unless you know a mod uses it.", "Open") {
                UScreen.displayScreen(ServersScreen())
            }
        }

        category("Rendering") {
            decimalSlider(::xOffset, "Position X Offset", hidden = true)
            decimalSlider(::yOffset, "Position Y Offset", hidden = true)

            selector(::anchorPointOrdinal, "Anchor Point", "The Point at which the display will be anchored to.", Anchor.values().map { it.toString() }) {
                // The setter should have already synced anchorPoint, but ensure it's synced
                // Force sync by reading the ordinal value and ensuring enum is in sync
                val currentOrdinal = anchorPointOrdinal
                anchorPoint = Anchor.values()[currentOrdinal.coerceIn(0, Anchor.values().size - 1)]
                xOffset = anchorPoint.getDefaultXOffset()
                yOffset = anchorPoint.getDefaultYOffset()
                markDirty()
                writeData()
                // Update player position immediately - force update by calling twice
                Player.changePosition(anchorPoint)
                // Also update offsets in case they weren't set correctly
                Utils.async {
                    Thread.sleep(50)
                    Player.changePosition(anchorPoint)
                }
            }
            selector(::renderTypeOrdinal, "Render Type", "How/When the song with display.", RenderType.values().map { it.toString() }) {
                // Setter syncs enum automatically
                markDirty()
                writeData()
            }
            selector(::displayModeOrdinal, "Display Mode", "When it will display.", DisplayMode.values().map { it.toString() }) {
                // Setter syncs enum automatically
                markDirty()
                writeData()
            }
            // hudScale is registered via @Property annotation above, but we need to register a callback
            // Register it manually to get the callback
            try {
                val hudScaleProperty = this::class.java.getDeclaredField("hudScale")
                hudScaleProperty.isAccessible = true
                // The @Property annotation will handle the UI, but we need to add a callback
                // We'll use registerListener for this instead
            } catch (e: Exception) {
                // Fallback - register manually
            }
            boolean(::premiumControl, "Controls", "Will allow you to pause/play, skip forward and backwards, repeat, and shuffle the music in game. (Requires Spotify Premium)")
            boolean(::streamerMode, "Streamer Mode", "Will mark the overlay as an overlay in OBS, meaning if you turn off show overlays it won't be shown.\nThis requires the obs-overlay mod to be installed.")
        }

        // Check if config file exists BEFORE initializing
        val configFile = File("./config/craftify.toml")
        val configFileExists = configFile.exists()
        
        // If config file exists, read values from it to restore them if Vigilant doesn't load them
        var fileAnchorPoint: Int? = null
        var fileHudScale: Float? = null
        var fileLinkMode: Int? = null
        var fileRenderType: Int? = null
        var fileDisplayMode: Int? = null
        
        if (configFileExists) {
            try {
                val content = configFile.readText()
                // Try multiple patterns - TOML might have quotes or different formatting
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
                        fileAnchorPoint = match.groupValues[1].toIntOrNull()
                        break
                    }
                }
                
                val scalePatterns = listOf(
                    Regex("hud_scale\\s*=\\s*([\\d.]+)"),
                    Regex("\"hud_scale\"\\s*=\\s*([\\d.]+)"),
                    Regex("'hud_scale'\\s*=\\s*([\\d.]+)"),
                    Regex("hud_scale\\s*=\\s*\"([\\d.]+)\""),
                    Regex("hud_scale\\s*=\\s*'([\\d.]+)'")
                )
                for (pattern in scalePatterns) {
                    val match = pattern.find(content)
                    if (match != null) {
                        fileHudScale = match.groupValues[1].toFloatOrNull()
                        break
                    }
                }
                
                val linkPatterns = listOf(
                    Regex("link_mode\\s*=\\s*(\\d+)"),
                    Regex("\"link_mode\"\\s*=\\s*(\\d+)"),
                    Regex("'link_mode'\\s*=\\s*(\\d+)"),
                    Regex("link_mode\\s*=\\s*\"(\\d+)\""),
                    Regex("link_mode\\s*=\\s*'(\\d+)'")
                )
                for (pattern in linkPatterns) {
                    val match = pattern.find(content)
                    if (match != null) {
                        fileLinkMode = match.groupValues[1].toIntOrNull()
                        break
                    }
                }
                
                val renderPatterns = listOf(
                    Regex("render_type\\s*=\\s*(\\d+)"),
                    Regex("\"render_type\"\\s*=\\s*(\\d+)"),
                    Regex("'render_type'\\s*=\\s*(\\d+)"),
                    Regex("render_type\\s*=\\s*\"(\\d+)\""),
                    Regex("render_type\\s*=\\s*'(\\d+)'")
                )
                for (pattern in renderPatterns) {
                    val match = pattern.find(content)
                    if (match != null) {
                        fileRenderType = match.groupValues[1].toIntOrNull()
                        break
                    }
                }
                
                val displayPatterns = listOf(
                    Regex("display_mode\\s*=\\s*(\\d+)"),
                    Regex("\"display_mode\"\\s*=\\s*(\\d+)"),
                    Regex("'display_mode'\\s*=\\s*(\\d+)"),
                    Regex("display_mode\\s*=\\s*\"(\\d+)\""),
                    Regex("display_mode\\s*=\\s*'(\\d+)'")
                )
                for (pattern in displayPatterns) {
                    val match = pattern.find(content)
                    if (match != null) {
                        fileDisplayMode = match.groupValues[1].toIntOrNull()
                        break
                    }
                }
            } catch (e: Exception) {
                // Ignore errors reading file
            }
        }
        
        initialize()
        
        // Restore values from file if they exist and Vigilant didn't load them correctly
        // (UIPlayer reads from file directly for anchor point, but we still need to sync Config object)
        if (configFileExists) {
            if (fileHudScale != null && fileHudScale >= 0.1f && kotlin.math.abs(hudScale - fileHudScale) > 0.001f) {
                hudScale = fileHudScale
            }
            if (fileLinkMode != null && fileLinkMode >= 0 && linkModeOrdinal != fileLinkMode) {
                linkModeOrdinal = fileLinkMode.coerceIn(0, LinkingMode.values().size - 1)
            }
            if (fileRenderType != null && fileRenderType >= 0 && renderTypeOrdinal != fileRenderType) {
                renderTypeOrdinal = fileRenderType.coerceIn(0, RenderType.values().size - 1)
            }
            if (fileDisplayMode != null && fileDisplayMode >= 0 && displayModeOrdinal != fileDisplayMode) {
                displayModeOrdinal = fileDisplayMode.coerceIn(0, DisplayMode.values().size - 1)
            }
            if (fileAnchorPoint != null && fileAnchorPoint >= 0 && anchorPointOrdinal != fileAnchorPoint) {
                anchorPointOrdinal = fileAnchorPoint.coerceIn(0, Anchor.values().size - 1)
            }
        }
        
        // Sync enum fields from ordinal values (setters handle this automatically)
        // Ensure hudScale is valid (default is 1.0f, setter clamps to 0.1f-5.0f)
        
        // Sync enums and update offsets
        linkMode = LinkingMode.values()[linkModeOrdinal.coerceIn(0, LinkingMode.values().size - 1)]
        anchorPoint = Anchor.values()[anchorPointOrdinal.coerceIn(0, Anchor.values().size - 1)]
        renderType = RenderType.values()[renderTypeOrdinal.coerceIn(0, RenderType.values().size - 1)]
        displayMode = DisplayMode.values()[displayModeOrdinal.coerceIn(0, DisplayMode.values().size - 1)]
        xOffset = anchorPoint.getDefaultXOffset()
        yOffset = anchorPoint.getDefaultYOffset()
        
        // ONLY write to config file if it doesn't exist (first time setup)
        // If file exists, don't touch it - just read from it
        if (!configFileExists && firstTime) {
            // First time - set defaults and create config file
            anchorPointOrdinal = Anchor.TOP_LEFT.ordinal
            hudScale = 1.0f
            linkModeOrdinal = LinkingMode.OPEN.ordinal
            renderTypeOrdinal = RenderType.NON_INTRUSIVE.ordinal
            displayModeOrdinal = DisplayMode.WHEN_SONG_FOUND.ordinal
            linkMode = LinkingMode.values()[linkModeOrdinal]
            anchorPoint = Anchor.values()[anchorPointOrdinal]
            renderType = RenderType.values()[renderTypeOrdinal]
            displayMode = DisplayMode.values()[displayModeOrdinal]
            xOffset = anchorPoint.getDefaultXOffset()
            yOffset = anchorPoint.getDefaultYOffset()
            markDirty()
            writeData()
        }
        
        // Register listeners for property changes (OneConfig might bypass callbacks)
        // These listeners will fire when properties are changed, even if callbacks don't
        try {
            registerListener("anchorPointOrdinal") { _: Any ->
                anchorPoint = Anchor.values()[anchorPointOrdinal.coerceIn(0, Anchor.values().size - 1)]
                xOffset = anchorPoint.getDefaultXOffset()
                yOffset = anchorPoint.getDefaultYOffset()
                Player.changePosition(anchorPoint)
                markDirty()
                writeData()
            }
            registerListener("hudScale") { _: Any ->
                // Setter already clamps the value, just trigger UI update
                Player.updateTheme()
                markDirty()
                writeData()
            }
            registerListener("linkModeOrdinal") { _: Any ->
                linkMode = LinkingMode.values()[linkModeOrdinal.coerceIn(0, LinkingMode.values().size - 1)]
                markDirty()
                writeData()
            }
            registerListener("renderTypeOrdinal") { _: Any ->
                renderType = RenderType.values()[renderTypeOrdinal.coerceIn(0, RenderType.values().size - 1)]
                markDirty()
                writeData()
            }
            registerListener("displayModeOrdinal") { _: Any ->
                displayMode = DisplayMode.values()[displayModeOrdinal.coerceIn(0, DisplayMode.values().size - 1)]
                markDirty()
                writeData()
            }
        } catch (e: Exception) {
            // registerListener might not be available, that's okay
        }
        
        // Update player with current config values
        Player.changePosition(anchorPoint)
        Player.updateTheme()
     }
 }

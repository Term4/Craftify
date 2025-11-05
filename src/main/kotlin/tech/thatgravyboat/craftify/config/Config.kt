package tech.thatgravyboat.craftify.config

import gg.essential.universal.UScreen
import gg.essential.vigilance.Vigilant
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
    var linkMode = LinkingMode.OPEN
    var linkModeOrdinal: Int = LinkingMode.OPEN.ordinal
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
    var anchorPoint = Anchor.TOP_LEFT
    var anchorPointOrdinal: Int = Anchor.TOP_LEFT.ordinal
        get() = field
        set(value) {
            field = value.coerceIn(0, Anchor.values().size - 1)
            anchorPoint = Anchor.values()[field]
        }
    
    var renderType = RenderType.NON_INTRUSIVE
    var renderTypeOrdinal: Int = RenderType.NON_INTRUSIVE.ordinal
        get() = field
        set(value) {
            field = value.coerceIn(0, RenderType.values().size - 1)
            renderType = RenderType.values()[field]
        }
    
    var displayMode = DisplayMode.WHEN_SONG_FOUND
    var displayModeOrdinal: Int = DisplayMode.WHEN_SONG_FOUND.ordinal
        get() = field
        set(value) {
            field = value.coerceIn(0, DisplayMode.values().size - 1)
            displayMode = DisplayMode.values()[field]
        }
    var premiumControl = false
    var streamerMode = false
    var xOffset = 0f
    var yOffset = 0f
    var hudScale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 5.0f)
        }
        get() {
            // Ensure value is always within valid range
            return field.coerceIn(0.1f, 5.0f)
        }

    init {
        category("General") {
            switch(::firstTime, "First Time", hidden = true)

            prop<ServiceProperty>(::musicService, "Music Service", "What service you want to use for fetching the music you're listening to?")
            selector(::linkModeOrdinal, "Link Mode", "How you will get/display the link when you click on the link button.", LinkingMode.values().map { it.toString() }) {
                linkMode = LinkingMode.values()[linkModeOrdinal]
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
                anchorPoint = Anchor.values()[anchorPointOrdinal]
                xOffset = anchorPoint.getDefaultXOffset()
                yOffset = anchorPoint.getDefaultYOffset()
                markDirty()
                writeData()
                // Update player position immediately, even if player hasn't been initialized yet
                // The change will be applied when the player is created
                Player.changePosition(anchorPoint)
            }
            selector(::renderTypeOrdinal, "Render Type", "How/When the song with display.", RenderType.values().map { it.toString() }) {
                renderType = RenderType.values()[renderTypeOrdinal]
                markDirty()
                writeData()
            }
            selector(::displayModeOrdinal, "Display Mode", "When it will display.", DisplayMode.values().map { it.toString() }) {
                displayMode = DisplayMode.values()[displayModeOrdinal]
                markDirty()
                writeData()
            }
            decimalSlider(::hudScale, "HUD Scale", "Scale factor for the HUD overlay. 1.0 = normal size, 0.5 = half size, 2.0 = double size. Recommended range: 0.5 to 3.0.") {
                markDirty()
                writeData()
                Player.updateTheme()
            }
            boolean(::premiumControl, "Controls", "Will allow you to pause/play, skip forward and backwards, repeat, and shuffle the music in game. (Requires Spotify Premium)")
            boolean(::streamerMode, "Streamer Mode", "Will mark the overlay as an overlay in OBS, meaning if you turn off show overlays it won't be shown.\nThis requires the obs-overlay mod to be installed.")
        }

        initialize()
        
        // After initialize(), Vigilant has loaded the config
        // Sync enum fields from ordinal properties (which were loaded by Vigilant)
        // This ensures the enum fields match what was saved
        linkMode = LinkingMode.values()[linkModeOrdinal]
        anchorPoint = Anchor.values()[anchorPointOrdinal]
        renderType = RenderType.values()[renderTypeOrdinal]
        displayMode = DisplayMode.values()[displayModeOrdinal]
        
        // Ensure hudScale is within valid range after load
        hudScale = hudScale.coerceIn(0.1f, 5.0f)
        
        // Update player with current config values
        Player.changePosition(anchorPoint)
        Player.updateTheme()
    }
}

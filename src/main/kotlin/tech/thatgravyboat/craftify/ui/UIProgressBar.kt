package tech.thatgravyboat.craftify.ui

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMouse
import org.lwjgl.input.Mouse
import tech.thatgravyboat.craftify.Initializer
import tech.thatgravyboat.craftify.config.Config
import tech.thatgravyboat.craftify.themes.ThemeConfig
import tech.thatgravyboat.craftify.ui.constraints.ConfigColorConstraint
import tech.thatgravyboat.craftify.ui.constraints.ThemeFontProvider
import tech.thatgravyboat.craftify.utils.Utils
import tech.thatgravyboat.jukebox.api.service.ServiceType
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

class UIProgressBar(
    private val getEditModeState: (() -> Boolean)? = null
) : UIRoundedRectangle(ThemeConfig.progressRadius) {

    private var stop: Boolean = false
    private var timer: AtomicInteger = AtomicInteger(0)
    private var end: Int = 1
    private var start: Int = 1
    private var isDragging: Boolean = false
    private var isSeeking: Boolean = false // Flag to prevent timer updates while seeking
    private var lastSeekApiCall: Long = 0 // Timestamp of last API seek call
    private var pendingSeekPosition: Int? = null // Position to seek to when drag ends
    private val SEEK_THROTTLE_MS = 200L // Only send API calls every 200ms during dragging

    init {
        constrain {
            color = ConfigColorConstraint("progress_background")
        }

        Utils.schedule(1, 1, TimeUnit.SECONDS) {
            if (!stop && !isSeeking && Initializer.getAPI()?.getState()?.isPlaying == true && timer.get() < end) {
                timer.incrementAndGet()
                update()
            }
        }
        
        // Mouse click/drag to seek
        onMouseClick { event ->
            if (event.mouseButton == 0 && Config.premiumControl) { // Left click, only if controls are enabled
                // Check if parent UIPlayer is in edit mode - don't allow seeking in edit mode
                if (getEditModeState?.invoke() == true) {
                    return@onMouseClick
                }
                isDragging = true
                handleSeek(event.relativeX)
            }
        }
        
        onMouseRelease {
            isDragging = false
        }
    }
    
    // Handle seek based on mouse X position
    private fun handleSeek(relativeX: Float) {
        if (end <= 0) return
        
        val width = getWidth()
        if (width <= 0) return
        
        // Calculate percentage (0.0 to 1.0)
        val percent = (relativeX / width).coerceIn(0f, 1f)
        
        // Calculate new position in seconds
        val newPosition = (percent * end).toInt().coerceIn(0, end)
        // Spotify API typically uses milliseconds, so convert to milliseconds
        val newPositionMs = newPosition * 1000
        
        // Update timer immediately for visual feedback
        timer.set(newPosition)
        isSeeking = true
        
        // Update the bar immediately WITHOUT animation for instant feedback
        updateBarImmediately(newPosition)
        
        // Store pending seek position (will be sent when dragging stops)
        pendingSeekPosition = newPosition
        
        // Seek in the API - throttle during dragging, but always update visual
        val currentTime = System.currentTimeMillis()
        // Only throttle if we're actively dragging; first click should always work
        val shouldMakeApiCall = if (isDragging) {
            // During drag, throttle to once per SEEK_THROTTLE_MS
            currentTime - lastSeekApiCall >= SEEK_THROTTLE_MS
        } else {
            // First click or not dragging, always make the call
            true
        }
        
        if (shouldMakeApiCall) {
            lastSeekApiCall = currentTime
            performSeekApiCall(newPosition, newPositionMs)
        }
    }
    
    // Perform the actual API seek call (separated for throttling)
    private fun performSeekApiCall(newPosition: Int, newPositionMs: Int) {
        Utils.async {
            try {
                val api = Initializer.getAPI()
                if (api != null) {
                    // Check if this is SpotifyService - it doesn't support seeking via jukebox API
                    // We'll need to use direct Spotify Web API calls
                    if (api is tech.thatgravyboat.jukebox.impl.spotify.SpotifyService) {
                        // Try to seek using Spotify Web API directly
                        try {
                            // Try to get token via reflection since it might be a private property
                            var token: String? = null
                            try {
                                // Try direct property access first
                                val tokenField = api::class.java.getDeclaredField("token")
                                tokenField.isAccessible = true
                                token = tokenField.get(api) as? String
                            } catch (e: Exception) {
                                // Try getter method
                                try {
                                    val tokenMethod = api::class.java.getMethod("getToken")
                                    token = tokenMethod.invoke(api) as? String
                                } catch (e2: Exception) {
                                    // Try property access
                                    try {
                                        token = api.token
                                    } catch (e3: Exception) {
                                        println("[Craftify] Could not access token: ${e3.message}")
                                    }
                                }
                            }
                            
                            // Also try getting from config
                            if (token.isNullOrBlank()) {
                                token = tech.thatgravyboat.craftify.services.config.SpotifyServiceConfig.auth
                            }
                            
                            if (!token.isNullOrBlank()) {
                                // Spotify Web API seek endpoint: PUT https://api.spotify.com/v1/me/player/seek?position_ms={position_ms}
                                val url = java.net.URL("https://api.spotify.com/v1/me/player/seek?position_ms=$newPositionMs")
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = "PUT"
                                connection.setRequestProperty("Authorization", "Bearer $token")
                                connection.setRequestProperty("Content-Type", "application/json")
                                connection.setRequestProperty("Content-Length", "0")
                                connection.doOutput = true
                                connection.connectTimeout = 5000
                                connection.readTimeout = 5000
                                
                                // Connect and write empty body (required for some servers)
                                connection.connect()
                                connection.outputStream.use { it.write(ByteArray(0)) }
                                
                                val responseCode = connection.responseCode
                                
                                if (responseCode in 200..204) {
                                    // Success - no need to spam console
                                    return@async
                                } else {
                                    println("[Craftify] Spotify Web API seek failed with code: $responseCode")
                                    val errorStream = connection.errorStream
                                    if (errorStream != null) {
                                        val error = errorStream.bufferedReader().use { it.readText() }
                                        println("[Craftify] Error response: $error")
                                    } else {
                                        val inputStream = connection.inputStream
                                        if (inputStream != null) {
                                            val response = inputStream.bufferedReader().use { it.readText() }
                                            println("[Craftify] Response: $response")
                                        }
                                    }
                                }
                                connection.disconnect()
                            } else {
                                println("[Craftify] No access token available for Spotify (token is null or blank)")
                            }
                        } catch (e: Exception) {
                            println("[Craftify] Failed to seek via Spotify Web API: ${e.message}")
                            e.printStackTrace()
                        }
                        // If direct API call failed, seeking is not supported
                        return@async
                    }
                    
                    // For other services, try reflection-based approach
                    var seekSuccess = false
                    
                    // Get all methods from the API class and its superclasses
                    val allMethods = mutableListOf<java.lang.reflect.Method>()
                    var clazz: Class<*>? = api::class.java
                    while (clazz != null) {
                        allMethods.addAll(clazz.declaredMethods)
                        clazz = clazz.superclass
                    }
                    
                    // Try common seek method names first
                    val seekMethodNames = listOf("seek", "seekTo", "setPosition", "seekPosition", "setProgress", "setSeek", "jumpTo", "seekToPosition")
                    
                    for (methodName in seekMethodNames) {
                        val methods = allMethods.filter { it.name.equals(methodName, ignoreCase = true) }
                        for (method in methods) {
                            try {
                                method.isAccessible = true // Make private methods accessible
                                
                                // Try methods with 1 parameter
                                if (method.parameterCount == 1) {
                                    val paramType = method.parameterTypes[0]
                                    when {
                                        paramType == Int::class.java || paramType == Int::class.javaPrimitiveType -> {
                                            // Try seconds first
                                            try {
                                                method.invoke(api, newPosition)
                                                seekSuccess = true
                                                println("[Craftify] Successfully called ${method.name}($newPosition) - seconds")
                                                break
                                            } catch (e: Exception) {
                                                // Try milliseconds if seconds failed
                                                try {
                                                    method.invoke(api, newPositionMs)
                                                    seekSuccess = true
                                                    println("[Craftify] Successfully called ${method.name}($newPositionMs) - milliseconds")
                                                    break
                                                } catch (e2: Exception) {
                                                    println("[Craftify] Failed to call ${method.name}: ${e2.message}")
                                                }
                                            }
                                        }
                                        paramType == Long::class.java || paramType == Long::class.javaPrimitiveType -> {
                                            // Try seconds first
                                            try {
                                                method.invoke(api, newPosition.toLong())
                                                seekSuccess = true
                                                println("[Craftify] Successfully called ${method.name}(${newPosition}L) - seconds")
                                                break
                                            } catch (e: Exception) {
                                                // Try milliseconds if seconds failed
                                                try {
                                                    method.invoke(api, newPositionMs.toLong())
                                                    seekSuccess = true
                                                    println("[Craftify] Successfully called ${method.name}(${newPositionMs}L) - milliseconds")
                                                    break
                                                } catch (e2: Exception) {
                                                    println("[Craftify] Failed to call ${method.name}: ${e2.message}")
                                                }
                                            }
                                        }
                                        paramType == Float::class.java || paramType == Float::class.javaPrimitiveType -> {
                                            method.invoke(api, newPosition.toFloat())
                                            seekSuccess = true
                                            println("[Craftify] Successfully called ${method.name}(${newPosition}f)")
                                            break
                                        }
                                        paramType == Double::class.java || paramType == Double::class.javaPrimitiveType -> {
                                            method.invoke(api, newPosition.toDouble())
                                            seekSuccess = true
                                            println("[Craftify] Successfully called ${method.name}(${newPosition}.0)")
                                            break
                                        }
                                        else -> {
                                            // Try with any type - might be a custom position class
                                            try {
                                                method.invoke(api, newPosition)
                                                seekSuccess = true
                                                println("[Craftify] Successfully called ${method.name} with int parameter")
                                                break
                                            } catch (e: Exception) {
                                                try {
                                                    method.invoke(api, newPositionMs)
                                                    seekSuccess = true
                                                    println("[Craftify] Successfully called ${method.name} with int parameter (ms)")
                                                    break
                                                } catch (e2: Exception) {
                                                    // Try with String representation
                                                    try {
                                                        method.invoke(api, newPosition.toString())
                                                        seekSuccess = true
                                                        println("[Craftify] Successfully called ${method.name} with string parameter")
                                                        break
                                                    } catch (e3: Exception) {
                                                        // Continue
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                // Also try methods with 0 parameters (might need to set position first)
                                else if (method.parameterCount == 0) {
                                    // Some APIs might have a seek() method that uses a previously set position
                                    try {
                                        method.invoke(api)
                                        seekSuccess = true
                                        println("[Craftify] Successfully called ${method.name}() - no parameters")
                                        break
                                    } catch (e: Exception) {
                                        // Continue
                                    }
                                }
                            } catch (e: Exception) {
                                // Try next method - method might exist but have wrong signature
                                println("[Craftify] Error calling ${method.name}: ${e.message}")
                            }
                        }
                        if (seekSuccess) break
                    }
                    
                    // If still no success, try looking for methods with "position" or "progress" in name
                    if (!seekSuccess) {
                        val positionMethods = allMethods.filter { 
                            (it.name.contains("position", ignoreCase = true) || 
                            it.name.contains("progress", ignoreCase = true) ||
                            it.name.contains("seek", ignoreCase = true)) &&
                            it.parameterCount == 1
                        }
                        for (method in positionMethods) {
                            try {
                                method.isAccessible = true
                                val paramType = method.parameterTypes[0]
                                when {
                                    paramType == Int::class.java || paramType == Int::class.javaPrimitiveType -> {
                                        // Try seconds first
                                        try {
                                            method.invoke(api, newPosition)
                                            seekSuccess = true
                                            break
                                        } catch (e: Exception) {
                                            // Try milliseconds
                                            try {
                                                method.invoke(api, newPositionMs)
                                                seekSuccess = true
                                                break
                                            } catch (e2: Exception) {
                                                // Continue
                                                println("[Craftify] Seek attempt failed with ${method.name}(int): ${e2.message}")
                                            }
                                        }
                                    }
                                    paramType == Long::class.java || paramType == Long::class.javaPrimitiveType -> {
                                        // Try seconds first
                                        try {
                                            method.invoke(api, newPosition.toLong())
                                            seekSuccess = true
                                            break
                                        } catch (e: Exception) {
                                            // Try milliseconds
                                            try {
                                                method.invoke(api, newPositionMs.toLong())
                                                seekSuccess = true
                                                break
                                            } catch (e2: Exception) {
                                                // Continue
                                                println("[Craftify] Seek attempt failed with ${method.name}(long): ${e2.message}")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Try next method
                            }
                        }
                    }
                    
                    // Debug: Print all available methods if seeking failed
                    if (!seekSuccess) {
                        println("[Craftify] Seeking failed on ${api::class.java.simpleName}. Trying to find seek method...")
                        println("[Craftify] Attempted position: ${newPosition}s (${newPositionMs}ms)")
                        println("[Craftify] All methods with any parameters that might be seek-related:")
                        // Print ALL methods, not just primitive ones
                        allMethods.filter { 
                            val name = it.name.lowercase()
                            name.contains("seek") || name.contains("position") || name.contains("progress") ||
                            name.contains("time") || name.contains("jump") || name.contains("skip")
                        }.forEach {
                            val params = it.parameterTypes.joinToString(", ") { param -> param.simpleName }
                            println("  - ${it.name}($params)")
                        }
                        // Also check if there's a method with no parameters or multiple parameters
                        println("[Craftify] Checking for methods that might control position (all methods):")
                        allMethods.filter { it.parameterCount <= 3 }.take(20).forEach {
                            val params = it.parameterTypes.joinToString(", ") { param -> param.simpleName }
                            println("  - ${it.name}($params)")
                        }
                    } else {
                        println("[Craftify] Seek successful! Position: ${newPosition}s")
                    }
                }
            } catch (e: Exception) {
                // Service might not support seeking - that's okay
                println("[Craftify] Error during seek: ${e.message}")
                e.printStackTrace() // Debug: print to see what's happening
            } finally {
                // Re-enable timer updates after a short delay
                Utils.async {
                    Thread.sleep(500)
                    isSeeking = false
                }
            }
        }
    }
    
    // Update bar immediately without animation during seeking
    private fun updateBarImmediately(position: Int) {
        if (position == 0 || end == 0) {
            // Set width directly without animation
            bar.setWidth(0.percent())
        } else {
            // Calculate percentage and set width directly without animation
            val percentWidth = (position / end.toDouble()) * 100
            // Set width directly - this bypasses animation
            bar.setWidth(percentWidth.percent())
        }
        // Update time display
        startTime.setText("${floor((position / 60).toDouble()).toInt()}:${(position % 60).let { if (it < 10) "0$it" else "$it" }}")
    }
    
    // Called during render to handle continuous dragging
    fun updateDrag() {
        if (!isDragging || !Mouse.isButtonDown(0)) {
            if (!isDragging) {
                // Mouse released - send final seek position if we have one pending
                pendingSeekPosition?.let { position ->
                    val positionMs = position * 1000
                    performSeekApiCall(position, positionMs)
                    pendingSeekPosition = null
                }
                // Re-enable timer updates
                isSeeking = false
            }
            isDragging = false
            return
        }
        
        // Get mouse position relative to this component
        val mouseX = UMouse.Scaled.x.toFloat()
        val componentLeft = getLeft()
        val relativeX = mouseX - componentLeft
        
        // Allow seeking even if slightly outside bounds (for smooth dragging at edges)
        if (relativeX >= -10 && relativeX <= getWidth() + 10) {
            val clampedX = relativeX.coerceIn(0f, getWidth())
            handleSeek(clampedX)
        }
    }

    private val bar = UIRoundedRectangle(ThemeConfig.progressRadius).constrain {
        width = 0.percent()
        height = 100.percent()
        color = ConfigColorConstraint("progress_bar")
    } childOf this

    private val startTime by lazy { UIText("0:00").constrain {
        y = (-6).pixel()
        textScale = 0.5.pixel()
        color = ConfigColorConstraint("progress_text")
        fontProvider = ThemeFontProvider("progress")
    } childOf this }

    private val endTime by lazy { UIText("0:00").constrain {
        x = 100.percent() - "0:00".width(0.5f).pixel()
        y = (-6).pixel()
        color = ConfigColorConstraint("progress_text")
        fontProvider = ThemeFontProvider("progress")
        textScale = 0.5.pixel()
    } childOf this }

    fun tempStop() {
        stop = true
    }

    fun updateTime(start: Int, end: Int) {
        stop = false
        timer.set(start)
        this.end = end
        this.start = start
        update()
    }

    private fun update() {
        // Don't update if we're currently seeking (to prevent conflicts)
        if (isSeeking) return
        
        val start = if ((Initializer.getAPI()?.getServiceType() ?: ServiceType.UNKNOWN) != ServiceType.WEBSOCKET) timer.get() else this.start
        if (start == 0) {
            bar.setWidth(0.percent())
        } else {
            bar.animate {
                val newWidth = ((start / end.toDouble()) * 100).percent()
                val newWidthValue = newWidth.getWidth(bar)
                setWidthAnimation(Animations.LINEAR, if (this.width.getWidth(bar) > newWidthValue) 0f else 1f, ((start / end.toDouble()) * 100).percent())
            }
        }
        startTime.setText("${floor((start / 60).toDouble()).toInt()}:${(start % 60).let { if (it < 10) "0$it" else "$it" }}")
        endTime.setText("${floor((end / 60).toDouble()).toInt()}:${(end % 60).let { if (it < 10) "0$it" else "$it" }}")
        endTime.constraints.x = endTime.getText().width(0.5f).pixel(alignOpposite = true) + endTime.getText().width(0.5f).pixel()
    }

    fun updateTheme() {
        bar.setRadius(ThemeConfig.progressRadius.pixels())
        this.setRadius(ThemeConfig.progressRadius.pixels())
    }
}

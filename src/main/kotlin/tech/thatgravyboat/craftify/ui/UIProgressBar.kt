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
        
        // Update timer immediately for visual feedback
        timer.set(newPosition)
        isSeeking = true
        
        // Update the bar immediately WITHOUT animation for instant feedback
        updateBarImmediately(newPosition)
        
        // Seek in the API - try multiple methods
        Utils.async {
            try {
                val api = Initializer.getAPI()
                if (api != null) {
                    // Try multiple possible method names and signatures
                    var seekSuccess = false
                    
                    // Get all methods from the API class and its superclasses
                    val allMethods = mutableListOf<java.lang.reflect.Method>()
                    var clazz: Class<*>? = api::class.java
                    while (clazz != null) {
                        allMethods.addAll(clazz.declaredMethods)
                        clazz = clazz.superclass
                    }
                    
                    // Try common seek method names first
                    val seekMethodNames = listOf("seek", "seekTo", "setPosition", "seekPosition", "setProgress", "setSeek")
                    
                    for (methodName in seekMethodNames) {
                        val methods = allMethods.filter { it.name.equals(methodName, ignoreCase = true) }
                        for (method in methods) {
                            try {
                                method.isAccessible = true // Make private methods accessible
                                if (method.parameterCount == 1) {
                                    val paramType = method.parameterTypes[0]
                                    when {
                                        paramType == Int::class.java -> {
                                            method.invoke(api, newPosition)
                                            seekSuccess = true
                                            break
                                        }
                                        paramType == Long::class.java -> {
                                            method.invoke(api, newPosition.toLong())
                                            seekSuccess = true
                                            break
                                        }
                                        paramType == Float::class.java -> {
                                            method.invoke(api, newPosition.toFloat())
                                            seekSuccess = true
                                            break
                                        }
                                        paramType == Double::class.java -> {
                                            method.invoke(api, newPosition.toDouble())
                                            seekSuccess = true
                                            break
                                        }
                                        paramType == Int::class.javaPrimitiveType -> {
                                            method.invoke(api, newPosition)
                                            seekSuccess = true
                                            break
                                        }
                                        paramType == Long::class.javaPrimitiveType -> {
                                            method.invoke(api, newPosition.toLong())
                                            seekSuccess = true
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Try next method - method might exist but have wrong signature
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
                                        method.invoke(api, newPosition)
                                        seekSuccess = true
                                        break
                                    }
                                    paramType == Long::class.java || paramType == Long::class.javaPrimitiveType -> {
                                        method.invoke(api, newPosition.toLong())
                                        seekSuccess = true
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                // Try next method
                            }
                        }
                    }
                    
                    // Debug: Print all available methods if seeking failed
                    if (!seekSuccess) {
                        println("[Craftify] Seeking failed. Available methods on ${api::class.java.simpleName}:")
                        allMethods.filter { it.parameterCount == 1 && it.parameterTypes[0].isPrimitive }.forEach {
                            println("  - ${it.name}(${it.parameterTypes[0].simpleName})")
                        }
                    }
                }
            } catch (e: Exception) {
                // Service might not support seeking - that's okay
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
                // Mouse released, re-enable timer updates
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

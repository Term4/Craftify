package tech.thatgravyboat.craftify.ui.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ColorConstraint
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import tech.thatgravyboat.craftify.themes.ThemeConfig
import java.awt.Color

class ConfigColorConstraint(private val id: String) : ColorConstraint {
    override var cachedValue: Color = Color.WHITE
    override var recalculate = true

    override fun getColorImpl(component: UIComponent): Color {
        // Always recalculate to get fresh values
        if (!recalculate) {
            recalculate = true
        }
        val color = when (id) {
            "border" -> ThemeConfig.borderColor
            "artist" -> ThemeConfig.artistColor
            "title" -> ThemeConfig.titleColor
            "background" -> {
                // Use Config.backgroundColor (it syncs with ThemeConfig.backgroundColor)
                // But also check ThemeConfig in case Config hasn't synced yet
                val configBg = tech.thatgravyboat.craftify.config.Config.backgroundColor
                if (configBg != null && configBg.alpha > 0) {
                    configBg
                } else {
                    ThemeConfig.backgroundColor
                }
            }
            "progress_background" -> ThemeConfig.progressBackgroundColor
            "progress_bar" -> ThemeConfig.progressColor
            "progress_text" -> ThemeConfig.progressNumberColor
            else -> cachedValue
        }
        cachedValue = color
        return color
    }

    override fun to(component: UIComponent) = apply {
        throw UnsupportedOperationException("Constraint.to(UIComponent) is not available in this context!")
    }

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}

    override var constrainTo: UIComponent? = null
}

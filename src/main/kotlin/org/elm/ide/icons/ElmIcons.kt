package org.elm.ide.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

object ElmIcons {


    /**
     * Basic file icon, matching the rest of IntelliJ's file icons
     */
    val FILE = getIcon("elm-file.png")


    /**
     * Monochromatic Elm icon suitable for toolwindow (it's also smaller than the normal icons)
     */
    val TOOL_WINDOW = getIcon("elm-toolwindow.png")
    val ELM_ANIMATED = AnimatedIcon(AnimatedIcon.Default.DELAY, TOOL_WINDOW, TOOL_WINDOW.rotated(15.0), TOOL_WINDOW.rotated(30.0), TOOL_WINDOW.rotated(45.0),
        TOOL_WINDOW.rotated(60.0), TOOL_WINDOW.rotated(75.0), TOOL_WINDOW.rotated(90.0),
        TOOL_WINDOW.rotated(105.0), TOOL_WINDOW.rotated(120.0), TOOL_WINDOW.rotated(135.0),
        TOOL_WINDOW.rotated(150.0), TOOL_WINDOW.rotated(165.0), TOOL_WINDOW.rotated(180.0),
        TOOL_WINDOW.rotated(195.0), TOOL_WINDOW.rotated(210.0), TOOL_WINDOW.rotated(225.0),
        TOOL_WINDOW.rotated(240.0), TOOL_WINDOW.rotated(255.0), TOOL_WINDOW.rotated(270.0),
        TOOL_WINDOW.rotated(285.0), TOOL_WINDOW.rotated(300.0), TOOL_WINDOW.rotated(315.0),
        TOOL_WINDOW.rotated(330.0), TOOL_WINDOW.rotated(345.0), TOOL_WINDOW.rotated(360.0)
    )



    /**
     * Colorful Elm icon
     */
    val COLORFUL = getIcon("elm-colorful.png")


    /**
     * Gutter icon for values and types exposed by an Elm module
     */
    val EXPOSED_GUTTER = getIcon("elm-exposure.png")

    val RECURSIVE_CALL = AllIcons.Gutter.RecursiveMethod

    // STRUCTURE VIEW ICONS


    val FUNCTION = getIcon("function.png")
    val VALUE = getIcon("value.png")
    val UNION_TYPE = getIcon("type.png")
    val TYPE_ALIAS = getIcon("type.png")


    private fun getIcon(path: String): Icon {
        return IconLoader.getIcon("/icons/$path", ElmIcons::class.java)
    }
}

/**
 * Rotates the icon by the given angle, in degrees.
 *
 * **Important**: Do ***not*** rotate the icon by ±90 degrees (or any sufficiently close amount)!
 * The implementation of rotation by that amount in AWT is broken, and results in erratic shifts for composed
 * transformations. In other words, the (final) transformation matrix as a function of rotation angle
 * is discontinuous at those points.
 */
fun Icon.rotated(angle: Double): Icon {
    val q = this
    return object : Icon by this {
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val g2d = g.create() as Graphics2D
            try {
                g2d.translate(x.toDouble(), y.toDouble())
                g2d.rotate(Math.toRadians(angle), iconWidth / 2.0, iconHeight / 2.0)
                q.paintIcon(c, g2d, 0, 0)
            } finally {
                g2d.dispose()
            }
        }
    }
}

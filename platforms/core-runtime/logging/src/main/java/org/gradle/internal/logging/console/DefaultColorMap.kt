/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.logging.console

import com.google.common.collect.Lists
import org.fusesource.jansi.Ansi
import org.gradle.internal.logging.console.ColorMap.Color
import org.gradle.internal.logging.text.Style
import org.gradle.internal.logging.text.StyledTextOutput

class DefaultColorMap(
    /**
     * Disables color, keeps emphasis.
     */
    private val noColor: Boolean
) : ColorMap {
    /**
     * Maps a [StyledTextOutput.Style] to the default color spec (that can be overridden by system properties)
     */
    private val defaults: MutableMap<String, String> = HashMap<String, String>()

    /**
     * Maps a [StyledTextOutput.Style] to the [Color] that has been created for it
     */
    private val colorByStyle: MutableMap<String, ColorMap.Color> = HashMap<String, ColorMap.Color>()

    /**
     * Maps a color spec to the [Color] that has been created for it
     */
    private val colorBySpec: MutableMap<String, ColorMap.Color> = HashMap<String, ColorMap.Color>()

    private val noDecoration: ColorMap.Color = object : ColorMap.Color {
        override fun on(ansi: Ansi) {
        }

        override fun off(ansi: Ansi) {
        }
    }

    internal constructor() : this(false)

    init {
        addDefault(StyledTextOutput.Style.Info, "yellow")
        addDefault(StyledTextOutput.Style.Error, "default")
        addDefault(StyledTextOutput.Style.Header, "bold")
        addDefault(StyledTextOutput.Style.Description, "yellow")
        addDefault(StyledTextOutput.Style.ProgressStatus, "yellow")
        addDefault(StyledTextOutput.Style.Identifier, "green")
        addDefault(StyledTextOutput.Style.UserInput, "bold")
        addDefault(StyledTextOutput.Style.Success, "green")
        addDefault(StyledTextOutput.Style.SuccessHeader, StyledTextOutput.Style.Success, StyledTextOutput.Style.Header)
        addDefault(StyledTextOutput.Style.Failure, "red")
        addDefault(StyledTextOutput.Style.FailureHeader, StyledTextOutput.Style.Failure, StyledTextOutput.Style.Header)
        addDefault(STATUS_BAR, "bold")
    }


    private fun addDefault(style: StyledTextOutput.Style, colorSpec: String) {
        addDefault(style.name.lowercase(), colorSpec)
    }

    private fun addDefault(style: String, color: String) {
        defaults.put(style, color)
    }

    private fun addDefault(style: StyledTextOutput.Style, vararg styles: StyledTextOutput.Style) {
        var colorSpec = getColorSpecForStyle(styles[0])
        for (i in 1..<styles.size) {
            colorSpec += COLOR_DIVIDER + getColorSpecForStyle(styles[i])
        }
        addDefault(style.name.lowercase(), colorSpec)
    }

    val statusBarColor: ColorMap.Color
        get() = getColor(STATUS_BAR)

    override fun getColourFor(style: StyledTextOutput.Style): ColorMap.Color {
        return getColor(style.name.lowercase())
    }

    override fun getColourFor(style: Style): ColorMap.Color {
        val colors: MutableList<ColorMap.Color> = ArrayList<ColorMap.Color>()
        for (emphasis in style.emphasises) {
            if (emphasis == Style.Emphasis.BOLD) {
                colors.add(newBoldColor())
            } else if (emphasis == Style.Emphasis.REVERSE) {
                colors.add(newReverseColor())
            } else if (emphasis == Style.Emphasis.ITALIC) {
                colors.add(newItalicColor())
            }
        }

        if (!noColor) {
            if (style.color == Style.Color.GREY) {
                colors.add(BrightForegroundColor(Ansi.Color.BLACK))
            } else {
                val ansiColor = Ansi.Color.valueOf(style.color.name.uppercase())
                if (ansiColor != Ansi.Color.DEFAULT) {
                    colors.add(ForegroundColor(ansiColor))
                }
            }
        }

        return CompositeColor(colors)
    }

    private fun getColor(style: String): ColorMap.Color {
        var color = colorByStyle.get(style)
        if (color == null) {
            color = createColor(style)
            colorByStyle.put(style, color)
        }

        return color
    }

    private fun getColorSpecForStyle(style: StyledTextOutput.Style): String {
        return getColorSpecForStyle(style.name.lowercase())
    }

    private fun getColorSpecForStyle(style: String): String {
        return System.getProperty("org.gradle.color." + style, defaults.get(style))
    }

    private fun createColor(style: String): ColorMap.Color {
        val colorSpec = getColorSpecForStyle(style)

        var color = noDecoration
        if (colorSpec != null) {
            color = createColorFromSpec(colorSpec)
            colorBySpec.put(colorSpec, color)
        }

        return color
    }

    private fun createColorFromSpec(colorSpec: String): ColorMap.Color {
        val cachedColor = colorBySpec.get(colorSpec)
        if (cachedColor != null) {
            return cachedColor
        }

        if (colorSpec.equals(BOLD, ignoreCase = true)) {
            return newBoldColor()
        }
        if (colorSpec.equals("reverse", ignoreCase = true)) {
            return newReverseColor()
        }
        if (colorSpec.equals("italic", ignoreCase = true)) {
            return newItalicColor()
        }

        if (colorSpec.contains("-")) {
            val colors = colorSpec.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val colorList = ArrayList<ColorMap.Color>(colors.size)
            for (color in colors) {
                colorList.add(createColorFromSpec(color))
            }
            return CompositeColor(colorList)
        }

        if (noColor) {
            return noDecoration
        }

        val ansiColor = Ansi.Color.valueOf(colorSpec.uppercase())
        if (ansiColor != Ansi.Color.DEFAULT) {
            return ForegroundColor(ansiColor)
        }

        return noDecoration
    }

    private class BrightForegroundColor(private val ansiColor: Ansi.Color) : ColorMap.Color {
        override fun on(ansi: Ansi) {
            ansi.fgBright(ansiColor)
        }

        override fun off(ansi: Ansi) {
            ansi.fg(Ansi.Color.DEFAULT)
        }
    }

    private class ForegroundColor(private val ansiColor: Ansi.Color) : ColorMap.Color {
        override fun on(ansi: Ansi) {
            ansi.fg(ansiColor)
        }

        override fun off(ansi: Ansi) {
            ansi.fg(Ansi.Color.DEFAULT)
        }
    }

    private class AttributeColor(private val on: Ansi.Attribute, private val off: Ansi.Attribute) : ColorMap.Color {
        override fun on(ansi: Ansi) {
            ansi.a(on)
        }

        override fun off(ansi: Ansi) {
            ansi.a(off)
        }
    }

    private class CompositeColor(private val colors: MutableList<ColorMap.Color>) : ColorMap.Color {
        override fun on(ansi: Ansi) {
            for (color in colors) {
                color.on(ansi)
            }
        }

        override fun off(ansi: Ansi) {
            for (color in Lists.reverse<ColorMap.Color>(colors)) {
                color.off(ansi)
            }
        }
    }

    companion object {
        private const val STATUS_BAR = "statusbar"
        private const val BOLD = "bold"
        private const val COLOR_DIVIDER = "-"

        private fun newBoldColor(): ColorMap.Color {
            // We don't use Attribute.INTENSITY_BOLD_OFF as it's rarely supported like Windows 10
            return AttributeColor(Ansi.Attribute.INTENSITY_BOLD, Ansi.Attribute.RESET)
        }

        private fun newReverseColor(): ColorMap.Color {
            return AttributeColor(Ansi.Attribute.NEGATIVE_ON, Ansi.Attribute.NEGATIVE_OFF)
        }

        private fun newItalicColor(): ColorMap.Color {
            return AttributeColor(Ansi.Attribute.ITALIC, Ansi.Attribute.ITALIC_OFF)
        }
    }
}

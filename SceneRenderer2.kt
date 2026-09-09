package clos_grapher_element

import java.awt.*
import java.awt.event.*
import java.awt.image.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.swing.*
import kotlin.math.*

class SceneRenderer2 : JFrame() {

    init {
        title = "Scene Renderer - SDF2 Scalar Field, but v2"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(1280, 800)
        setLocationRelativeTo(null)

        val panel = ScenePanel2()

        // Header
        val header = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.drawLine(0, height - 1, width, height - 1)
            }
        }.apply {
            preferredSize = Dimension(0, 46)
            isOpaque = false
        }
        val title = JLabel("  SCENE RENDERER, but v2").apply {
            font = Font("Monospaced", Font.BOLD, 13)
            foreground = Color(255, 255, 255)
        }
        header.add(title, BorderLayout.WEST)

        // Control strip
        val ctrl = JPanel(FlowLayout(FlowLayout.LEFT, 14, 10)).apply {
            isOpaque = false
            preferredSize = Dimension(0, 52)
        }

        val scenes = arrayOf("Sphere + Plane", "Cube + Sphere", "Torus + Floor", "Pillars", "Custom Mix")
        val sceneBox = JComboBox(scenes).also { styleCombo(it) }
        sceneBox.addActionListener { panel.setScene(sceneBox.selectedIndex); panel.scheduleRender() }

        val shades = arrayOf("Full (D+S+Shadow+AO)", "Diffuse Only", "Normals", "Depth", "AO")
        val shadeBox = JComboBox(shades).also { styleCombo(it) }
        shadeBox.addActionListener { panel.setShadingMode(shadeBox.selectedIndex); panel.scheduleRender() }

        val qualSlider = JSlider(1, 4, 2).apply {
            isOpaque = false
            foreground = Color(255, 255, 255)
            preferredSize = Dimension(100, 26)
        }
        qualSlider.addChangeListener { panel.setQuality(qualSlider.value); panel.scheduleRender() }

        val renderBtn = JButton("RENDER").apply {
            font = Font("Monospaced", Font.BOLD, 11)
            background = Color(0, 0, 0)
            foreground = Color(255, 255, 255)
            border = BorderFactory.createLineBorder(Color(255, 255, 255), 1)
            isFocusPainted = false
        }
        renderBtn.addActionListener { panel.scheduleRender() }

        ctrl.add(mkLabel("SCENE"));   ctrl.add(sceneBox)
        ctrl.add(mkLabel("SHADING")); ctrl.add(shadeBox)
        ctrl.add(mkLabel("QUALITY")); ctrl.add(qualSlider)
        ctrl.add(renderBtn)
        ctrl.add(mkLabel("   Drag=rotate  Scroll=zoom  R=re-render"))

        val root = JPanel(BorderLayout()).apply { background = Color(0, 0, 0) }
        root.add(header, BorderLayout.NORTH)
        root.add(panel,  BorderLayout.CENTER)
        root.add(ctrl,   BorderLayout.SOUTH)
        contentPane = root

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).apply {
            put(KeyStroke.getKeyStroke('r'), "render")
            put(KeyStroke.getKeyStroke('R'), "render")
        }
        root.actionMap.put("render", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = panel.scheduleRender()
        })
    }

    private fun mkLabel(t: String) = JLabel(t).apply {
        font = Font("Monospaced", Font.BOLD, 10)
        foreground = Color(255, 255, 255)
    }

    private fun styleCombo(c: JComboBox<*>) {
        c.background = Color(0, 0, 0)
        c.foreground = Color(255, 255, 255)
        c.font = Font("Monospaced", Font.PLAIN, 12)
        c.border = BorderFactory.createLineBorder(Color(255, 255, 255), 1)
        c.preferredSize = Dimension(220, 28)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()) }
            catch (_: Exception) {}
            SwingUtilities.invokeLater { SceneRenderer2().isVisible = true }
        }
    }
}

// todo: rendering logic and engine

class SphereSDF2(
    private val cx: Double, private val cy: Double, private val cz: Double,
    private val r: Double,
    cr: Float, cg: Float, cb: Float
) : SDF2 {
    private val rgb = ((cr * 255).toInt() shl 16) or ((cg * 255).toInt() shl 8) or (cb * 255).toInt()

    override fun evalXYZ(px: Double, py: Double, pz: Double): Double {
        val dx = px - cx; val dy = py - cy; val dz = pz - cz
        return sqrt(dx * dx + dy * dy + dz * dz) - r
    }
    override fun baseRGB() = rgb
}

class PlaneSDF2(
    private val y0: Double,
    cr: Float, cg: Float, cb: Float,
    private val checker: Boolean
) : SDF2 {
    private val rgb      = ((cr * 255).toInt() shl 16) or ((cg * 255).toInt() shl 8) or (cb * 255).toInt()
    private val rgbWhite = ((0.90 * 255).toInt() shl 16) or ((0.88 * 255).toInt() shl 8) or (0.85 * 255).toInt()

    override fun evalXYZ(px: Double, py: Double, pz: Double) = py - y0
    override fun baseRGB() = rgb
    override fun colorRGB(px: Double, py: Double, pz: Double): Int {
        if (!checker) return rgb
        return if ((floor(px * 0.7).toInt() + floor(pz * 0.7).toInt()) and 1 == 0) rgbWhite else rgb
    }
}

class RoundBoxSDF2(
    private val cx: Double, private val cy: Double, private val cz: Double,
    private val hx: Double, private val hy: Double, private val hz: Double,
    private val rad: Float,
    cr: Float, cg: Float, cb: Float
) : SDF2 {
    private val rgb = ((cr * 255).toInt() shl 16) or ((cg * 255).toInt() shl 8) or (cb * 255).toInt()

    override fun evalXYZ(px: Double, py: Double, pz: Double): Double {
        val qx = abs(px - cx) - hx; val qy = abs(py - cy) - hy; val qz = abs(pz - cz) - hz
        val ex = maxOf(qx, 0.0); val ey = maxOf(qy, 0.0); val ez = maxOf(qz, 0.0)
        return sqrt(ex * ex + ey * ey + ez * ez) + minOf(maxOf(qx, maxOf(qy, qz)), 0.0) - rad
    }
    override fun baseRGB() = rgb
}

class TorusSDF2(
    private val cx: Double, private val cy: Double, private val cz: Double,
    private val R: Double, private val r: Double,
    cr: Float, cg: Float, cb: Float
) : SDF2 {
    private val rgb = ((cr * 255).toInt() shl 16) or ((cg * 255).toInt() shl 8) or (cb * 255).toInt()

    override fun evalXYZ(px: Double, py: Double, pz: Double): Double {
        val dx = px - cx; val dy = py - cy; val dz = pz - cz
        val q = sqrt(dx * dx + dz * dz) - R
        return sqrt(q * q + dy * dy) - r
    }
    override fun baseRGB() = rgb
}

class CylinderSDF2(
    private val bx: Double, private val by: Double, private val bz: Double,
    private val rad: Double, private val height: Double,
    cr: Float, cg: Float, cb: Float
) : SDF2 {
    private val rgb = ((cr * 255).toInt() shl 16) or ((cg * 255).toInt() shl 8) or (cb * 255).toInt()

    override fun evalXYZ(px: Double, py: Double, pz: Double): Double {
        val dx = px - bx; val dz = pz - bz
        val d  = sqrt(dx * dx + dz * dz) - rad
        val dy = abs(py - by - height * .5) - height * .5
        val ex = maxOf(d, 0.0); val ey = maxOf(dy, 0.0)
        return minOf(maxOf(d, dy), 0.0) + sqrt(ex * ex + ey * ey)
    }
    override fun baseRGB() = rgb
}
import java.awt.*
import java.awt.event.*
import java.awt.image.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.swing.*
import kotlin.math.*

class SceneRenderer2 : JFrame() {

    init {
        title = "Scene Renderer - SDF2 Scalar Field, but v2 and in Kotlin"
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

class ScenePanel2 : JPanel() {

    private var yaw   = 30.0
    private var pitch = 25.0
    private var zoom  = 1.0
    private var lastMX = 0
    private var lastMY = 0
    private var sceneIndex  = 0
    private var shadingMode = 0
    private var quality     = 2

    @Volatile private var buffer: BufferedImage? = null
    @Volatile private var statusMsg = "Press RENDER or drag to start"

    private val generation = AtomicInteger(0)

    private val renderExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "renderer").also { it.isDaemon = true }
    }
    private val tilePool = ForkJoinPool(maxOf(1, Runtime.getRuntime().availableProcessors()))

    init {
        background = Color(8, 8, 18)

        val ma = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent)  { lastMX = e.x; lastMY = e.y }
            override fun mouseDragged(e: MouseEvent)  {
                yaw   += (e.x - lastMX) * 0.4
                pitch += (e.y - lastMY) * 0.4
                pitch  = pitch.coerceIn(-80.0, 80.0)
                lastMX = e.x; lastMY = e.y
                scheduleRender()
            }
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                zoom *= 0.92.pow(e.wheelRotation.toDouble())
                zoom  = zoom.coerceIn(0.2, 5.0)
                scheduleRender()
            }
        }
        addMouseListener(ma); addMouseMotionListener(ma); addMouseWheelListener(ma)
    }

    fun setScene(i: Int)       { sceneIndex  = i }
    fun setShadingMode(m: Int) { shadingMode = m }
    fun setQuality(q: Int)     { quality     = q }

    fun scheduleRender() {
        val gen = generation.incrementAndGet()
        val W = width; val H = height
        if (W <= 0 || H <= 0) return

        val snapYaw   = yaw;   val snapPitch = pitch; val snapZoom  = zoom
        val snapScene = sceneIndex; val snapShade = shadingMode; val snapQual = quality

        statusMsg = "Rendering..."; repaint()

        renderExec.submit {
            if (generation.get() != gen) return@submit

            val t0 = System.currentTimeMillis()

            val rY = Math.toRadians(snapYaw);  val rP = Math.toRadians(snapPitch)
            val sY = sin(rY); val cY = cos(rY); val sP = sin(rP); val cP = cos(rP)
            val cdx0 = sY * cP; val cdy0 = sP; val cdz0 = cY * cP
            val cdLen = sqrt(cdx0 * cdx0 + cdy0 * cdy0 + cdz0 * cdz0)
            val cdx = cdx0 / cdLen; val cdy = cdy0 / cdLen; val cdz = cdz0 / cdLen
            // camRight = normalize(camDir × worldUp), worldUp=(0,1,0)
            var crx = -cdz; var crz = cdx
            val crLen = sqrt(crx * crx + crz * crz)
            if (crLen < 1e-12) { crx = 1.0; crz = 0.0 } else { crx /= crLen; crz /= crLen }
            // camUp = camRight × camDir  (cry = 0 simplifies the cross product)
            val cux = -crz * cdy; val cuy = crz * cdx - crx * cdz; val cuz = crx * cdy

            val sH = 4.0 / snapZoom; val sW = sH * (W.toDouble() / H)
            val rox0 = -cdx * 12.0; val roy0 = -cdy * 12.0; val roz0 = -cdz * 12.0

            val scene = buildScene(snapScene)
            val ss    = if (snapQual >= 3) 2 else 1
            val ssInv = 1.0 / ss

            val img    = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
            val pixels = (img.raster.dataBuffer as DataBufferInt).data

            val cores = tilePool.parallelism
            val band  = maxOf(1, (H + cores - 1) / cores)

            val fcrx = crx; val fcrz = crz
            val fcux = cux; val fcuy = cuy; val fcuz = cuz
            val fsW  = sW;  val fsH  = sH
            val frox = rox0; val froy = roy0; val froz = roz0
            val fcdx = cdx;  val fcdy = cdy;  val fcdz = cdz

            val rowsDone = AtomicInteger(0)
            val latch    = CountDownLatch(cores)

            for (b in 0 until cores) {
                val y0 = b * band; val y1 = minOf(H, (b + 1) * band)
                tilePool.submit tile@{
                    try {
                        for (py in y0 until y1) {
                            if (generation.get() != gen) return@tile
                            for (px in 0 until W) {
                                var rAcc = 0; var gAcc = 0; var bAcc = 0
                                for (sy in 0 until ss) {
                                    for (sx in 0 until ss) {
                                        val u = ((px + (sx + 0.5) * ssInv) / W - 0.5) * fsW
                                        val v = (0.5 - (py + (sy + 0.5) * ssInv) / H) * fsH
                                        val rox = frox + fcrx * u + fcux * v
                                        val roy = froy +             fcuy * v  // crY = 0
                                        val roz = froz + fcrz * u + fcuz * v
                                        val rgb = traceRay(rox, roy, roz, fcdx, fcdy, fcdz,
                                                           scene, snapShade, snapQual)
                                        rAcc += (rgb shr 16) and 0xFF
                                        gAcc += (rgb shr  8) and 0xFF
                                        bAcc +=  rgb          and 0xFF
                                    }
                                }
                                val ss2 = ss * ss
                                pixels[py * W + px] = ((rAcc / ss2) shl 16) or
                                                      ((gAcc / ss2) shl  8) or
                                                       (bAcc / ss2)
                            }
                            val done = rowsDone.incrementAndGet()
                            if (done % 24 == 0) {
                                val pct  = (100L * done / H).toInt()
                                val snap = img
                                SwingUtilities.invokeLater {
                                    if (generation.get() == gen) {
                                        buffer = snap; statusMsg = "Rendering… $pct%"; repaint()
                                    }
                                }
                            }
                        }
                    } finally { latch.countDown() }
                }
            }
            try { latch.await() } catch (_: InterruptedException) {}

            if (generation.get() != gen) return@submit
            buffer = img
            val ms = System.currentTimeMillis() - t0
            val sceneNames = arrayOf("Sphere+Plane","Cube+Sphere","Torus+Floor","Pillars","Custom")
            val shadeNames = arrayOf("Full","Diffuse","Normals","Depth","AO")
            val msg = "Rendered in ${ms}ms  |  ${sceneNames[snapScene]}  |  " +
                      "${shadeNames[snapShade]}  |  q=$snapQual  |  $cores cores  |  " +
                      "${"%.1f".format((W.toDouble() * H * ss * ss) / (ms * 1000.0))} Mpx/s"
            SwingUtilities.invokeLater { statusMsg = msg; repaint() }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        buffer?.let { g2.drawImage(it, 0, 0, width, height, null) }
        g2.color = Color(0, 0, 0, 160)
        g2.fillRect(0, height - 22, width, 22)
        g2.font  = Font("Monospaced", Font.PLAIN, 11)
        g2.color = Color(100, 200, 140)
        g2.drawString(statusMsg, 10, height - 7)
        g2.color = Color(255, 255, 255, 30)
        val cx = width / 2; val cy = height / 2
        g2.drawLine(cx - 8, cy, cx + 8, cy); g2.drawLine(cx, cy - 8, cx, cy + 8)
    }

    // static rendering logic

    companion object {
        private const val LUT_SIZE = 1024
        private val GAMMA_LUT = IntArray(LUT_SIZE) { i ->
            var v = i / LUT_SIZE.toDouble()
            v = v / (v + 1.0)
            (v.pow(1.0 / 2.2) * 255.0 + 0.5).toInt()
        }

        private fun enc(v: Double): Int {
            val i = (v * 256.0).toInt()
            return when {
                i < 0       -> 0
                i >= LUT_SIZE -> 255
                else          -> GAMMA_LUT[i]
            }
        }

        // Light direction
        private val LX: Double
        private val LY: Double
        private val LZ: Double
        init {
            val lx = 2.0; val ly = 4.0; val lz = 3.0
            val ll = sqrt(lx * lx + ly * ly + lz * lz)
            LX = lx / ll; LY = ly / ll; LZ = lz / ll
        }

        // pow32 via repeated squaring — replaces Math.pow(x, 32)
        // 5 multiplications vs exp(32·ln x). ~10× faster for specular highlights.
        private fun pow32(x: Double): Double {
            val x2 = x * x; val x4 = x2 * x2; val x8 = x4 * x4
            return x8 * x8 * x8 * x8
        }

        // ── Ray tracer — zero heap allocation in the entire hot path ──────────

        fun traceRay(
            rox: Double, roy: Double, roz: Double,
            rdx: Double, rdy: Double, rdz: Double,
            scene: Array<SDF2>, shadeMode: Int, quality: Int
        ): Int {
            var t = 0.0
            val maxDist  = 40.0
            val hitEps   = 9e-5
            val maxSteps = if (quality >= 3) 128 else 72
            var hitIdx   = -1
            var hpx = 0.0; var hpy = 0.0; var hpz = 0.0

            for (i in 0 until maxSteps) {
                val px = rox + rdx * t; val py = roy + rdy * t; val pz = roz + rdz * t
                var minD    = 1e18
                var nearest = -1
                for (s in scene.indices) {
                    val d = scene[s].evalXYZ(px, py, pz)
                    if (d < minD) { minD = d; nearest = s }
                }
                if (minD < hitEps) { hitIdx = nearest; hpx = px; hpy = py; hpz = pz; break }
                t += minD
                if (t > maxDist) break
            }

            if (hitIdx < 0) return skyRGB(rdx, rdy, rdz)

            // Surface normal via tetrahedron FD — 4 scene evals, not 5
            val ne = 1e-3
            val k0 = sceneMin(hpx + ne, hpy - ne, hpz - ne, scene)
            val k1 = sceneMin(hpx - ne, hpy - ne, hpz + ne, scene)
            val k2 = sceneMin(hpx - ne, hpy + ne, hpz - ne, scene)
            val k3 = sceneMin(hpx + ne, hpy + ne, hpz + ne, scene)
            val nnx =  k0 - k1 - k2 + k3
            val nny = -k0 - k1 + k2 + k3
            val nnz = -k0 + k1 - k2 + k3
            var nL = sqrt(nnx * nnx + nny * nny + nnz * nnz)
            if (nL < 1e-12) nL = 1.0
            val nx = nnx / nL; val ny = nny / nL; val nz = nnz / nL

            return when (shadeMode) {
                1 -> diffuseRGB(hpx, hpy, hpz, nx, ny, nz, scene[hitIdx])
                2 -> enc(nx * .5 + .5) shl 16 or (enc(ny * .5 + .5) shl 8) or enc(nz * .5 + .5)
                3 -> { val v = (1 - minOf(1.0, t / maxDist)).let { it * it }
                       enc(v * .6) shl 16 or (enc(v * .8) shl 8) or enc(v) }
                4 -> aoRGB(hpx, hpy, hpz, nx, ny, nz, scene)
                else -> fullRGB(hpx, hpy, hpz, nx, ny, nz, rdx, rdy, rdz, scene, hitIdx)
            }
        }

        // Scene SDF union
        private fun sceneMin(px: Double, py: Double, pz: Double, sc: Array<SDF2>): Double {
            var m = 1e18
            for (i in sc.indices) { val d = sc[i].evalXYZ(px, py, pz); if (d < m) m = d }
            return m
        }

        // Full shading
        private fun fullRGB(
            hpx: Double, hpy: Double, hpz: Double,
            nx: Double, ny: Double, nz: Double,
            rdx: Double, rdy: Double, rdz: Double,
            sc: Array<SDF2>, hitIdx: Int
        ): Int {
            val diff   = maxOf(0.0, nx * LX + ny * LY + nz * LZ)
            val shadow = softShadow(hpx + nx * 2e-3, hpy + ny * 2e-3, hpz + nz * 2e-3, sc)
            val hhx = LX - rdx; val hhy = LY - rdy; val hhz = LZ - rdz
            val hL = sqrt(hhx * hhx + hhy * hhy + hhz * hhz)
            val spec = pow32(maxOf(0.0, (nx * hhx + ny * hhy + nz * hhz) / hL))
            val ao   = ambOcc(hpx, hpy, hpz, nx, ny, nz, sc)
            val col  = sc[hitIdx].colorRGB(hpx, hpy, hpz)
            val br = ((col shr 16) and 0xFF) / 255.0
            val bg = ((col shr  8) and 0xFF) / 255.0
            val bb = ( col         and 0xFF) / 255.0
            val lit = 0.08 * ao + diff * shadow * 0.85 + spec * shadow * 0.35
            val sc2 = spec * shadow * 0.5
            return enc(br * lit + sc2) shl 16 or (enc(bg * lit + sc2) shl 8) or enc(bb * lit + sc2)
        }

        private fun diffuseRGB(
            hpx: Double, hpy: Double, hpz: Double,
            nx: Double, ny: Double, nz: Double, s: SDF2
        ): Int {
            val diff = maxOf(0.1, nx * LX + ny * LY + nz * LZ)
            val col  = s.colorRGB(hpx, hpy, hpz)
            return enc(((col shr 16) and 0xFF) / 255.0 * diff) shl 16 or
                   (enc(((col shr 8) and 0xFF) / 255.0 * diff) shl 8) or
                    enc((col and 0xFF) / 255.0 * diff)
        }

        private fun aoRGB(
            hpx: Double, hpy: Double, hpz: Double,
            nx: Double, ny: Double, nz: Double, sc: Array<SDF2>
        ): Int {
            val ao = ambOcc(hpx, hpy, hpz, nx, ny, nz, sc)
            return enc(ao * .4) shl 16 or (enc(ao * .5) shl 8) or enc(ao * .7)
        }

        // Shadow
        private fun softShadow(ox: Double, oy: Double, oz: Double, sc: Array<SDF2>): Double {
            var res = 1.0; var t = 0.02; val k = 8.0; val maxt = 20.0
            for (i in 0 until 48) {
                val h = sceneMin(ox + LX * t, oy + LY * t, oz + LZ * t, sc)
                if (h < 1e-4) return 0.0
                res = minOf(res, k * h / t); t += h; if (t > maxt) break
            }
            return maxOf(0.0, res)
        }

        // Ambient occlusion
        private fun ambOcc(
            px: Double, py: Double, pz: Double,
            nx: Double, ny: Double, nz: Double, sc: Array<SDF2>
        ): Double {
            var occ = 0.0; var s = 1.0
            for (i in 1..5) {
                val d = i * 0.2
                occ += s * (d - sceneMin(px + nx * d, py + ny * d, pz + nz * d, sc))
                s *= 0.5
            }
            return 1 - maxOf(0.0, minOf(1.0, occ * 2))
        }

        private fun skyRGB(rdx: Double, rdy: Double, rdz: Double): Int {
            val t = maxOf(0.0, rdy * .5 + .5)
            val r = .03 + t * .04; val g = .04 + t * .06; val b = .08 + t * .18
            // pow32(pow32(x)) = x^1024 — tight sun disc without Math.pow
            val sun = pow32(pow32(maxOf(0.0, rdx * LX + rdy * LY + rdz * LZ)))
            return enc(r + sun * .8) shl 16 or (enc(g + sun * .7) shl 8) or enc(b + sun * .3)
        }

        // Scene builder

        fun buildScene(idx: Int): Array<SDF2> = when (idx) {
            0 -> arrayOf(
                SphereSDF2(0.0, .8, 0.0, 1.4, .35f, .60f, 1.0f),
                PlaneSDF2(-0.6, .25f, .25f, .28f, true))

            1 -> arrayOf(
                RoundBoxSDF2(-.8, .5, 0.0, .9, .9, .9, .15f, 1f, .45f, .15f),
                SphereSDF2(1.2, .4, -.3, .9, .2f, .85f, .65f),
                PlaneSDF2(-0.6, .22f, .22f, .25f, true))

            2 -> arrayOf(
                TorusSDF2(0.0, .5, 0.0, 1.2, .4, .9f, .3f, .5f),
                PlaneSDF2(-0.6, .20f, .22f, .25f, true))

            3 -> Array<SDF2>(10) { i ->
                if (i < 8) {
                    val b = i / 2
                    val a = b * Math.PI / 2
                    val bx = cos(a) * 2.2; val bz = sin(a) * 2.2
                    if (i % 2 == 0) CylinderSDF2(bx, -.6, bz, .35, 2.5, .7f, .6f, .5f)
                    else            SphereSDF2(bx, 1.9, bz, .42, .8f, .7f, .55f)
                } else if (i == 8) PlaneSDF2(-0.6, .30f, .28f, .26f, true)
                else                SphereSDF2(0.0, .8, 0.0, .7, .3f, .7f, 1f)
            }

            else -> arrayOf(
                SphereSDF2(-1.2, .6, .3, .9, .9f, .4f, .2f),
                TorusSDF2(1.0, .4, -.2, .8, .28, .2f, .8f, .9f),
                RoundBoxSDF2(0.0, .3, 1.2, .5, .5, .5, .1f, .7f, .9f, .3f),
                PlaneSDF2(-0.6, .22f, .22f, .26f, true))
        }
    }
}

interface SDF2 {
    fun evalXYZ(px: Double, py: Double, pz: Double): Double
    fun baseRGB(): Int
    fun colorRGB(px: Double, py: Double, pz: Double): Int = baseRGB()
}

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
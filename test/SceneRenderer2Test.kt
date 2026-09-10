import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Unit tests for SceneRenderer2.
 */
class SceneRenderer2Test {

    private val companion = ScenePanel2.Companion
    private val companionClass = companion.javaClass

    private fun enc(v: Double): Int {
        val m = companionClass.getDeclaredMethod("enc", Double::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(companion, v) as Int
    }

    private fun pow32(x: Double): Double {
        val m = companionClass.getDeclaredMethod("pow32", Double::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(companion, x) as Double
    }

    // SphereSDF2

    @Nested
    inner class SphereSDF2Tests {

        // Unit sphere at the origin, colour (1.0, 0.5, 0.25)
        private val sphere = SphereSDF2(0.0, 0.0, 0.0, 1.0, 1.0f, 0.5f, 0.25f)

        @Test fun `evalXYZ at centre equals negative radius`() =
            assertEquals(-1.0, sphere.evalXYZ(0.0, 0.0, 0.0), 1e-9)

        @Test fun `evalXYZ on surface is zero`() =
            assertEquals(0.0, sphere.evalXYZ(1.0, 0.0, 0.0), 1e-9)

        @Test fun `evalXYZ outside is positive`() =
            assertTrue(sphere.evalXYZ(2.0, 0.0, 0.0) > 0.0)

        @Test fun `evalXYZ inside is negative`() =
            assertTrue(sphere.evalXYZ(0.5, 0.0, 0.0) < 0.0)

        @Test fun `evalXYZ distance matches geometry`() {
            // Point at (3,4,0): dist to origin = 5, so SDF = 5 - r = 4
            assertEquals(4.0, sphere.evalXYZ(3.0, 4.0, 0.0), 1e-9)
        }

        @Test fun `evalXYZ is rotationally symmetric`() {
            // All axis-aligned unit vectors are on the surface
            assertEquals(0.0, sphere.evalXYZ( 1.0,  0.0,  0.0), 1e-9)
            assertEquals(0.0, sphere.evalXYZ(-1.0,  0.0,  0.0), 1e-9)
            assertEquals(0.0, sphere.evalXYZ( 0.0,  1.0,  0.0), 1e-9)
            assertEquals(0.0, sphere.evalXYZ( 0.0, -1.0,  0.0), 1e-9)
            assertEquals(0.0, sphere.evalXYZ( 0.0,  0.0,  1.0), 1e-9)
            assertEquals(0.0, sphere.evalXYZ( 0.0,  0.0, -1.0), 1e-9)
        }

        @Test fun `evalXYZ for off-centre sphere`() {
            // Sphere at (3, 4, 0), r=2: distance from origin = 5-2 = 3
            val s = SphereSDF2(3.0, 4.0, 0.0, 2.0, 1f, 1f, 1f)
            assertEquals(3.0, s.evalXYZ(0.0, 0.0, 0.0), 1e-9)
        }

        @Test fun `baseRGB encodes float colour channels correctly`() {
            val rgb = sphere.baseRGB()
            assertEquals(255, (rgb shr 16) and 0xFF)   // 1.0f * 255 = 255
            assertEquals(127, (rgb shr  8) and 0xFF)   // 0.5f * 255 → 127
            assertEquals(63,   rgb         and 0xFF)   // 0.25f * 255 → 63
        }

        @Test fun `colorRGB returns baseRGB regardless of position`() {
            val base = sphere.baseRGB()
            assertEquals(base, sphere.colorRGB(0.0,   0.0, 0.0))
            assertEquals(base, sphere.colorRGB(99.9, -5.0, 123.0))
        }
    }

    // PlaneSDF2

    @Nested
    inner class PlaneSDF2Tests {

        private val flat    = PlaneSDF2( 0.0, 0.5f, 0.5f, 0.5f, false)
        private val offset  = PlaneSDF2(-2.0, 0.5f, 0.5f, 0.5f, false)
        private val checker = PlaneSDF2( 0.0, 0.3f, 0.3f, 0.3f, true)

        // evalXYZ
        @Test fun `evalXYZ at plane y0 is zero`() =
            assertEquals(0.0, flat.evalXYZ(0.0, 0.0, 0.0), 1e-9)

        @Test fun `evalXYZ is independent of x and z`() {
            assertEquals(0.0, flat.evalXYZ(999.0, 0.0, -999.0), 1e-9)
        }

        @Test fun `evalXYZ above plane is positive`() =
            assertTrue(flat.evalXYZ(0.0, 1.0, 0.0) > 0.0)

        @Test fun `evalXYZ below plane is negative`() =
            assertTrue(flat.evalXYZ(0.0, -1.0, 0.0) < 0.0)

        @Test fun `evalXYZ matches signed height above offset plane`() {
            // Plane at y=-2; point at y=1: distance = 1 - (-2) = 3
            assertEquals(3.0, offset.evalXYZ(0.0, 1.0, 0.0), 1e-9)
        }

        // Flat colour
        @Test fun `no-checker colorRGB always returns baseRGB`() {
            val base = flat.baseRGB()
            assertEquals(base, flat.colorRGB(  0.0, 0.0,   0.0))
            assertEquals(base, flat.colorRGB(100.0, 0.0,   0.0))
            assertEquals(base, flat.colorRGB(  0.0, 0.0, 100.0))
        }

        // Checker pattern
        @Test fun `checker even cell returns white tile`() {
            val white = checker.colorRGB(0.0, 0.0, 0.0)
            assertNotEquals(checker.baseRGB(), white, "Even cell should not be the dark colour")
        }

        @Test fun `checker odd cell returns base colour`() {
            assertEquals(checker.baseRGB(), checker.colorRGB(2.0, 0.0, 0.0))
        }

        @Test fun `checker alternates across x`() {
            val c0 = checker.colorRGB(0.0, 0.0, 0.0)   // even
            val c1 = checker.colorRGB(2.0, 0.0, 0.0)   // odd
            assertNotEquals(c0, c1)
        }

        @Test fun `checker alternates across z`() {
            val c0 = checker.colorRGB(0.0, 0.0, 0.0)   // even
            val c1 = checker.colorRGB(0.0, 0.0, 2.0)   // odd
            assertNotEquals(c0, c1)
        }

        @Test fun `checker colour is independent of y`() {
            // y has no effect on the pattern — world-space XZ only
            val c1 = checker.colorRGB(1.5, 0.0, 1.5)
            val c2 = checker.colorRGB(1.5, 999.0, 1.5)
            assertEquals(c1, c2)
        }

        @Test fun `checker produces exactly two distinct colours`() {
            // Sample an even and an odd tile
            val white = checker.colorRGB(0.0, 0.0, 0.0)
            val dark  = checker.colorRGB(2.0, 0.0, 0.0)
            assertNotEquals(white, dark)
            // floor(0*0.7) + floor(2*0.7) = 0 + floor(1.4) = 0 + 1 = 1, odd → dark tile
            assertEquals(dark, checker.colorRGB(0.0, 0.0, 2.0))
        }
    }

    // RoundBoxSDF2

    @Nested
    inner class RoundBoxSDF2Tests {

        // Box at origin, half-extents 1×1×1, rounding 0.1
        private val box = RoundBoxSDF2(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.1f, 0.6f, 0.7f, 0.8f)

        @Test fun `evalXYZ at centre is deeply inside`() =
            assertTrue(box.evalXYZ(0.0, 0.0, 0.0) < -1.0)

        @Test fun `evalXYZ on face equals negative rounding radius`() =
            assertEquals((-0.1f).toDouble(), box.evalXYZ(1.0, 0.0, 0.0), 1e-12)

        @Test fun `evalXYZ outside in x is positive`() =
            assertTrue(box.evalXYZ(2.0, 0.0, 0.0) > 0.0)

        @Test fun `evalXYZ outside far corner is positive`() =
            assertTrue(box.evalXYZ(2.0, 2.0, 2.0) > 0.0)

        @Test fun `evalXYZ is symmetric across origin`() {
            val d1 = box.evalXYZ( 1.5,  0.5,  0.5)
            val d2 = box.evalXYZ(-1.5, -0.5, -0.5)
            assertEquals(d1, d2, 1e-9)
        }

        @Test fun `baseRGB encodes colour correctly`() {
            val rgb = box.baseRGB()
            assertEquals(153, (rgb shr 16) and 0xFF)   // 0.6f * 255 → 153
            assertEquals(178, (rgb shr  8) and 0xFF)   // 0.7f * 255 → 178
            assertEquals(204,  rgb         and 0xFF)   // 0.8f * 255 → 204
        }

        @Test fun `colorRGB delegates to baseRGB`() =
            assertEquals(box.baseRGB(), box.colorRGB(0.0, 0.0, 0.0))
    }

    // TorusSDF2

    @Nested
    inner class TorusSDF2Tests {

        // Torus at origin, major radius R=2, tube radius r=0.5
        private val torus = TorusSDF2(0.0, 0.0, 0.0, 2.0, 0.5, 0.9f, 0.3f, 0.5f)

        // At (R, 0, 0) = (2, 0, 0): the centre of the tube cross-section.
        @Test fun `evalXYZ at tube centre equals negative tube radius`() =
            assertEquals(-0.5, torus.evalXYZ(2.0, 0.0, 0.0), 1e-9)

        // At (0, 0, 0): inside the hole. q = sqrt(0) - 2 = -2, but abs: SDF = sqrt(4+0) - 0.5 = 1.5
        @Test fun `evalXYZ at hole centre is positive (R minus r)`() =
            assertEquals(1.5, torus.evalXYZ(0.0, 0.0, 0.0), 1e-9)

        // At (R+r, 0, 0) = (2.5, 0, 0): outer edge of tube, on the surface.
        @Test fun `evalXYZ on outer tube surface is zero`() =
            assertEquals(0.0, torus.evalXYZ(2.5, 0.0, 0.0), 1e-9)

        // At (R-r, 0, 0) = (1.5, 0, 0): inner edge of tube, on the surface.
        @Test fun `evalXYZ on inner tube surface is zero`() =
            assertEquals(0.0, torus.evalXYZ(1.5, 0.0, 0.0), 1e-9)

        @Test fun `evalXYZ far outside is positive`() =
            assertTrue(torus.evalXYZ(10.0, 0.0, 0.0) > 0.0)

        @Test fun `baseRGB encodes colour correctly`() {
            val rgb = torus.baseRGB()
            assertEquals(229, (rgb shr 16) and 0xFF)   // 0.9f * 255 → 229
            assertEquals(76,  (rgb shr  8) and 0xFF)   // 0.3f * 255 → 76
            assertEquals(127,  rgb         and 0xFF)   // 0.5f * 255 → 127
        }

        @Test fun `colorRGB delegates to baseRGB`() =
            assertEquals(torus.baseRGB(), torus.colorRGB(2.0, 0.0, 0.0))
    }

    // CylinderSDF2

    @Nested
    inner class CylinderSDF2Tests {

        // Cylinder at origin, radius 0.5, height 4 → extends from y=0 to y=4
        private val cyl = CylinderSDF2(0.0, 0.0, 0.0, 0.5, 4.0, 0.7f, 0.6f, 0.5f)

        // At (0, 2, 0): on axis, mid-height.
        @Test fun `evalXYZ on axis at mid-height equals negative radius`() =
            assertEquals(-0.5, cyl.evalXYZ(0.0, 2.0, 0.0), 1e-9)

        // At (0.5, 2, 0): on the curved surface at mid-height.
        @Test fun `evalXYZ on curved surface at mid-height is zero`() =
            assertEquals(0.0, cyl.evalXYZ(0.5, 2.0, 0.0), 1e-9)

        @Test fun `evalXYZ outside radially is positive`() =
            assertTrue(cyl.evalXYZ(2.0, 2.0, 0.0) > 0.0)

        @Test fun `evalXYZ above top cap is positive`() =
            assertTrue(cyl.evalXYZ(0.0, 10.0, 0.0) > 0.0)

        @Test fun `evalXYZ below base cap is positive`() =
            assertTrue(cyl.evalXYZ(0.0, -5.0, 0.0) > 0.0)

        @Test fun `baseRGB encodes colour correctly`() {
            val rgb = cyl.baseRGB()
            assertEquals(178, (rgb shr 16) and 0xFF)   // 0.7f * 255 → 178
            assertEquals(153, (rgb shr  8) and 0xFF)   // 0.6f * 255 → 153
            assertEquals(127,  rgb         and 0xFF)   // 0.5f * 255 → 127
        }

        @Test fun `colorRGB delegates to baseRGB`() =
            assertEquals(cyl.baseRGB(), cyl.colorRGB(0.0, 2.0, 0.0))
    }

    // buildScene

    @Nested
    inner class BuildSceneTests {

        @Test fun `scene 0 has 2 objects`() =
            assertEquals(2, ScenePanel2.buildScene(0).size)

        @Test fun `scene 1 has 3 objects`() =
            assertEquals(3, ScenePanel2.buildScene(1).size)

        @Test fun `scene 2 has 2 objects`() =
            assertEquals(2, ScenePanel2.buildScene(2).size)

        @Test fun `scene 3 has 10 objects`() =
            assertEquals(10, ScenePanel2.buildScene(3).size)

        @Test fun `scene 4 (else branch) has 4 objects`() =
            assertEquals(4, ScenePanel2.buildScene(4).size)

        @Test fun `scene 99 (out-of-range) hits else branch`() =
            assertEquals(4, ScenePanel2.buildScene(99).size)

        @Test fun `all scenes contain no null elements`() {
            for (idx in 0..4) {
                val scene = ScenePanel2.buildScene(idx)
                for ((i, sdf) in scene.withIndex())
                    assertNotNull(sdf, "Scene $idx, element $i is null")
            }
        }

        @Test fun `all scenes evaluate without exception at multiple points`() {
            val probes = listOf(
                Triple(0.0, 0.0, 0.0),
                Triple(1.0, 1.0, 1.0),
                Triple(-5.0, 5.0, -5.0)
            )
            for (idx in 0..4) {
                val scene = ScenePanel2.buildScene(idx)
                for ((px, py, pz) in probes)
                    assertDoesNotThrow { scene.forEach { it.evalXYZ(px, py, pz) } }
            }
        }

        @Test fun `all scenes have at least one plane (for ground plane)`() {
            for (idx in 0..4) {
                val scene = ScenePanel2.buildScene(idx)
                assertTrue(scene.any { it is PlaneSDF2 }, "Scene $idx has no ground plane")
            }
        }
    }

    // traceRay

    @Nested
    inner class TraceRayTests {

        private val scene  = ScenePanel2.buildScene(0)

        // Hit ray
        private val hOx = 0.0; private val hOy = 0.8; private val hOz = -10.0
        private val hDx = 0.0; private val hDy = 0.0; private val hDz = 1.0

        // Miss ray
        private val mOy = 100.0

        private fun hit(mode: Int, q: Int = 2) =
            ScenePanel2.traceRay(hOx, hOy, hOz, hDx, hDy, hDz, scene, mode, q)

        private fun miss(mode: Int, q: Int = 2) =
            ScenePanel2.traceRay(hOx, mOy, hOz, hDx, hDy, hDz, scene, mode, q)

        // Basic discrimination

        @Test fun `hit ray returns different colour from miss ray (mode 0)`() =
            assertNotEquals(hit(0), miss(0))

        @Test fun `hit ray returns different colour from miss ray (mode 1)`() =
            assertNotEquals(hit(1), miss(1))

        @Test fun `all shading modes produce valid RGB on a hit`() {
            for (mode in 0..4) {
                val rgb = hit(mode)
                assertTrue(rgb in 0..0xFFFFFF, "Mode $mode hit out of range: 0x${rgb.toString(16)}")
            }
        }

        @Test fun `all shading modes produce valid RGB on a miss (sky)`() {
            for (mode in 0..4) {
                val rgb = miss(mode)
                assertTrue(rgb in 0..0xFFFFFF, "Mode $mode miss out of range: 0x${rgb.toString(16)}")
            }
        }

        // Shading modes produce distinct outputs for the same hit

        @Test fun `full and diffuse modes differ on a hit`() =
            assertNotEquals(hit(0), hit(1),
                "Full shading should differ from diffuse (specular+shadow add brightness)")

        @Test fun `normal mode differs from full mode on a hit`() =
            assertNotEquals(hit(0), hit(2))

        @Test fun `depth mode differs from full mode on a hit`() =
            assertNotEquals(hit(0), hit(3))

        @Test fun `AO mode differs from full mode on a hit`() =
            assertNotEquals(hit(0), hit(4))

        // Mode-specific invariants

        @Test fun `normal mode channels are all within byte range`() {
            val rgb = hit(2)
            for (ch in listOf(rgb shr 16, rgb shr 8, rgb)) {
                assertTrue((ch and 0xFF) in 0..255)
            }
        }

        // Sky miss always returns the same colour regardless of shade mode because
        // traceRay calls skyRGB() immediately when hitIdx < 0, before the switch.
        @Test fun `miss produces the same colour in all shading modes`() {
            val skyColor = miss(0)
            for (mode in 1..4)
                assertEquals(skyColor, miss(mode),
                    "Sky colour should be mode-independent (mode $mode differs)")
        }

        // Depth mode: v = (1 - t/maxDist)²; hit at t≈8.6 gives v²≈0.38; miss is pure sky.
        // The sky path skips the depth branch, so hit and miss differ.
        @Test fun `depth mode hit differs from depth miss`() =
            assertNotEquals(hit(3), miss(3))

        // Quality flag

        @Test fun `quality 4 (128-step march) returns valid RGB`() {
            val rgb = hit(0, q = 4)
            assertTrue(rgb in 0..0xFFFFFF)
        }

        @Test fun `quality 1 (72-step march) returns valid RGB`() {
            val rgb = hit(0, q = 1)
            assertTrue(rgb in 0..0xFFFFFF)
        }

        // Multi-scene correctness

        @Test fun `all 5 scenes produce valid hit colours`() {
            for (idx in 0..4) {
                // Shoot at a wide area where any scene is likely to have geometry
                val sc = ScenePanel2.buildScene(idx)
                val rgb = ScenePanel2.traceRay(0.0, 0.8, -10.0, 0.0, 0.0, 1.0, sc, 0, 2)
                assertTrue(rgb in 0..0xFFFFFF, "Scene $idx produced invalid RGB")
            }
        }
    }

    // enc thing

    @Nested
    inner class EncTests {

        @Test fun `enc of 0 is 0`() =
            assertEquals(0, enc(0.0))

        @Test fun `enc of negative is clamped to 0`() =
            assertEquals(0, enc(-100.0))

        @Test fun `enc of very large value is clamped to 255`() =
            assertEquals(255, enc(1000.0))

        @Test fun `enc always returns a value within byte range`() {
            for (raw in listOf(-1.0, 0.0, 0.1, 0.5, 1.0, 2.0, 5.0, 100.0))
                assertTrue(enc(raw) in 0..255, "enc($raw) out of byte range")
        }

        @Test fun `enc is monotonically non-decreasing`() {
            var prev = enc(0.0)
            for (i in 1..40) {
                val curr = enc(i * 0.1)
                assertTrue(curr >= prev, "enc not monotone between step ${i-1} and $i")
                prev = curr
            }
        }

        @Test fun `enc applies Reinhard compression - large inputs stay below 255`() {
            // Without Reinhard, enc(3.5) could overflow; with it the output is bounded.
            assertTrue(enc(3.5) < 255, "Reinhard should compress large values below 255")
        }

        @Test fun `enc of 1 is less than enc of 2 (monotone)`() =
            assertTrue(enc(1.0) < enc(2.0))

        @Test fun `enc approaches 255 asymptotically`() {
            // Very large inputs converge; 500 and 600 should give 255.
            assertEquals(enc(500.0), enc(600.0))
        }
    }

    // pow32()

    @Nested
    inner class Pow32Tests {

        @Test fun `pow32 of 0 is 0`() =
            assertEquals(0.0, pow32(0.0), 1e-15)

        @Test fun `pow32 of 1 is 1`() =
            assertEquals(1.0, pow32(1.0), 1e-15)

        @Test fun `pow32 of 0_5 matches Math pow`() =
            assertEquals(0.5.pow(32.0), pow32(0.5), 1e-15)

        @Test fun `pow32 of 0_9 matches Math pow`() =
            assertEquals(0.9.pow(32.0), pow32(0.9), 1e-10)

        @Test fun `pow32 of 0_1 matches Math pow`() =
            assertEquals(0.1.pow(32.0), pow32(0.1), 1e-35)

        @Test fun `pow32 of 0_99 matches Math pow`() =
            assertEquals(0.99.pow(32.0), pow32(0.99), 1e-10)

        @Test fun `pow32 is monotonically non-decreasing on unit interval`() {
            var prev = pow32(0.0)
            for (i in 1..10) {
                val x    = i * 0.1
                val curr = pow32(x)
                assertTrue(curr >= prev, "pow32 not monotone at x=$x")
                prev = curr
            }
        }

        @Test fun `pow32 falls off steeply - pow32(0_5) is tiny`() {
            // 0.5^32 = ~2.3e-10 — ensures specular highlights are very tight
            assertTrue(pow32(0.5) < 1e-8)
        }

        @Test fun `pow32 result equals five-squaring by hand`() {
            val x = 0.75
            val x2 = x * x; val x4 = x2 * x2; val x8 = x4 * x4
            val expected = x8 * x8 * x8 * x8   // x^32
            assertEquals(expected, pow32(x), 1e-15)
        }
    }
}
package clos_grapher_element

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

        // Flat colour (no checker)
        @Test fun `no-checker colorRGB always returns baseRGB`() {
            val base = flat.baseRGB()
            assertEquals(base, flat.colorRGB(  0.0, 0.0,   0.0))
            assertEquals(base, flat.colorRGB(100.0, 0.0,   0.0))
            assertEquals(base, flat.colorRGB(  0.0, 0.0, 100.0))
        }

        // Checker pattern
        // Pattern: (floor(px*0.7) + floor(pz*0.7)) & 1
        //   px=0, pz=0 → 0+0=0, even → rgbWhite (not baseRGB)
        //   px=2, pz=0 → floor(1.4)+0=1, odd → baseRGB (dark tile)
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
}
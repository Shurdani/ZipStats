package com.zipstats.app.tracking

import android.location.Location
import com.zipstats.app.TestApplication
import com.zipstats.app.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SpeedCalculatorTest {

    private lateinit var calculator: SpeedCalculator

    @Before
    fun setUp() {
        calculator = SpeedCalculator(VehicleType.PATINETE)
    }

    @Test
    fun reset_clearsCurrentSpeed() {
        calculator.processLocation(location(speedKmh = 20f))
        Thread.sleep(150)

        calculator.reset()

        assertEquals(0f, calculator.getCurrentSpeed())
        assertTrue(calculator.isHealthy())
    }

    @Test
    fun acceptsValidCruisingSpeed() {
        calculator.processLocation(location(speedKmh = 18f, accuracy = 8f))
        Thread.sleep(150)

        val result = calculator.processLocation(location(speedKmh = 18f, accuracy = 8f))

        assertNotNull(result)
        assertEquals(18f, result!!.instantaneous, 0.5f)
        assertTrue(result.smoothed >= VehicleType.PATINETE.pauseSpeedThreshold)
    }

    @Test
    fun showsZeroWhenBelowPauseThreshold() {
        val result = calculator.processLocation(location(speedKmh = 2f, accuracy = 5f))

        assertNotNull(result)
        assertEquals(0f, result!!.smoothed)
    }

    @Test
    fun dropsToZeroWhenGpsReportsSpeedButNotMoving() {
        val lat = 41.3851
        val lon = 2.1734
        calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 15f))
        Thread.sleep(150)

        val stopped = calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 5.5f))

        assertNotNull(stopped)
        assertEquals(0f, stopped!!.smoothed)
    }

    @Test
    fun showsZeroWhenSpeedBelowPauseThreshold() {
        calculator.processLocation(location(speedKmh = 15f, accuracy = 5f))
        Thread.sleep(150)

        val result = calculator.processLocation(location(speedKmh = 3f, accuracy = 5f))

        assertNotNull(result)
        assertEquals(0f, result!!.smoothed)
    }

    @Test
    fun rejectsGpsSpikeWhenDistanceDoesNotMatch() {
        val lat = 41.3851
        val lon = 2.1734
        calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 18f, accuracy = 5f))
        Thread.sleep(150)

        // Chip 27 km/h pero casi sin desplazamiento → discrepancia > 12 km/h
        val held = calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 27f, accuracy = 5f))

        assertNotNull(held)
        assertEquals(18f, held!!.smoothed, 1.5f)
    }

    @Test
    fun dropsToZeroWhenGpsDriftsButLittleNetMovement() {
        val lat = 41.3851
        val lon = 2.1734
        calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 18f))
        Thread.sleep(150)

        // Parado: chip ~5.5 km/h sin desplazamiento neto (misma posición)
        var last: SpeedPair? = null
        repeat(3) {
            last = calculator.processLocation(location(lat = lat, lon = lon, speedKmh = 5.5f))
            Thread.sleep(120)
        }

        assertNotNull(last)
        assertEquals(0f, last!!.smoothed)
    }

    @Test
    fun showsSpeedFromDistanceWhenHardwareReportsZero() {
        val lat = 41.3851
        val lon = 2.1734
        calculator.processLocation(locationNoSpeed(lat, lon))
        Thread.sleep(200)

        // ~2 m en 200 ms ≈ 36 km/h — típico al empezar a rodar sin speed del chip
        val result = calculator.processLocation(locationNoSpeed(lat + 0.000018, lon))

        assertNotNull(result)
        assertTrue("debería mostrar velocidad al moverse sin speed del GPS", result!!.smoothed > 0f)
    }

    @Test
    fun rejectsImplausibleSpeedAboveMax() {
        val tooFastKmh = VehicleType.PATINETE.maxSpeed * 1.5f
        repeat(4) {
            calculator.processLocation(location(speedKmh = tooFastKmh, accuracy = 5f))
            Thread.sleep(150)
        }

        val rejected = calculator.processLocation(location(speedKmh = tooFastKmh, accuracy = 5f))
        Thread.sleep(150)

        // Tras varios rechazos consecutivos, la última lectura puede ser null
        assertTrue(rejected == null || rejected.smoothed == calculator.getCurrentSpeed())
        assertTrue(calculator.getStats().consecutiveRejectedUpdates > 0 || rejected == null)
    }

    @Test
    fun allowsAccelerationFromStop() {
        val first = calculator.processLocation(location(speedKmh = 0f, accuracy = 5f))
        assertNotNull(first)
        assertEquals(0f, first!!.smoothed)
        Thread.sleep(500)

        val accelerating = calculator.processLocation(location(speedKmh = 15f, accuracy = 5f))

        assertNotNull(accelerating)
        assertEquals(15f, accelerating!!.instantaneous, 0.5f)
        assertTrue(accelerating.smoothed > 0f)
    }

    @Test
    fun rejectsPoorAccuracyAfterConsecutiveFailures() {
        repeat(5) {
            calculator.processLocation(location(speedKmh = 15f, accuracy = 50f))
            Thread.sleep(150)
        }

        val result = calculator.processLocation(location(speedKmh = 15f, accuracy = 50f))

        assertNull(result)
        assertTrue(!calculator.isHealthy())
    }

    @Test
    fun maintainsSpeedWhenUpdatesAreTooFrequent() {
        val first = calculator.processLocation(location(speedKmh = 20f, accuracy = 5f))
        assertNotNull(first)

        // Menos de MIN_TIME_DELTA (100 ms) — debe mantener la velocidad anterior
        val second = calculator.processLocation(location(speedKmh = 25f, accuracy = 5f))

        assertNotNull(second)
        assertEquals(first!!.smoothed, second!!.smoothed)
    }

    @Test
    fun rejectsGpsJumpWithLargeDiscrepancy() {
        val baseLat = 41.3851
        val baseLon = 2.1734

        calculator.processLocation(
            location(lat = baseLat, lon = baseLon, speedKmh = 18f, accuracy = 5f),
        )
        Thread.sleep(150)

        calculator.processLocation(
            location(lat = baseLat + 0.0001, lon = baseLon, speedKmh = 18f, accuracy = 5f),
        )
        Thread.sleep(150)

        // Salto grande de posición con velocidad GPS baja → discrepancia alta
        var lastResult: SpeedPair? = null
        repeat(5) {
            lastResult = calculator.processLocation(
                location(lat = baseLat + 0.05, lon = baseLon, speedKmh = 5f, accuracy = 5f),
            )
            Thread.sleep(150)
        }

        assertTrue(
            lastResult == null ||
                lastResult!!.smoothed <= 18f ||
                calculator.getStats().consecutiveRejectedUpdates > 0,
        )
    }

    private fun location(
        lat: Double = 41.3851,
        lon: Double = 2.1734,
        speedKmh: Float,
        accuracy: Float = 8f,
    ): Location {
        return Location("test").apply {
            latitude = lat
            longitude = lon
            this.accuracy = accuracy
            speed = speedKmh / 3.6f
            time = System.currentTimeMillis()
        }
    }

    private fun locationNoSpeed(
        lat: Double,
        lon: Double,
        accuracy: Float = 8f,
    ): Location {
        return Location("test").apply {
            latitude = lat
            longitude = lon
            this.accuracy = accuracy
            time = System.currentTimeMillis()
        }
    }
}

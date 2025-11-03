package com.example.patineta

class DistanceCalculatorTest {
    @Test
    fun testHaversineDistance() {
        val pointA = Location("").apply {
            latitude = 41.0
            longitude = 2.0
        }
        val pointB = Location("").apply {
            latitude = 41.001
            longitude = 2.001
        }
        val distance = calculateDistance(pointA, pointB)
        assertTrue(distance > 0)
    }
}

package com.example

import com.example.data.model.InventoryItem
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCheckInStockCalculation() {
        val initialStock = 24
        val receivedUnits = 16
        val newStock = initialStock + receivedUnits
        assertEquals(40, newStock)
    }

    @Test
    fun testLowStockDetection() {
        val lowItem = InventoryItem(
            id = 1,
            commonName = "Hydraulic Hose",
            oldCode = "H-01",
            newCode = "890100",
            quantity = 3,
            minThreshold = 10
        )
        assertTrue(lowItem.isLowStock)

        val replenished = lowItem.copy(quantity = lowItem.quantity + 20)
        assertFalse(replenished.isLowStock)
    }

    @Test
    fun testSequentialOrderNumberParsingAndIncrementing() {
        val defaultStart = 33100
        val existingPullOrders = listOf("ORD-33100", "ORD-33101", "ORD-33104")
        val maxPullNumber = existingPullOrders.mapNotNull { it.substringAfter("ORD-").toIntOrNull() }.maxOrNull() ?: (defaultStart - 1)
        val nextPullOrder = "ORD-${maxOf(maxPullNumber + 1, defaultStart)}"
        assertEquals("ORD-33105", nextPullOrder)

        val existingPOs = listOf("PO-33100", "PO-33102")
        val maxPONumber = existingPOs.mapNotNull { it.substringAfter("PO-").toIntOrNull() }.maxOrNull() ?: (defaultStart - 1)
        val nextPO = "PO-${maxOf(maxPONumber + 1, defaultStart)}"
        assertEquals("PO-33103", nextPO)
    }

    @Test
    fun testReplenishmentCoveredVsNeedsPurchase() {
        val lowStockItem = InventoryItem(
            id = 10,
            commonName = "Ball Bearing 608RS",
            newCode = "890200",
            quantity = 3,
            minThreshold = 10
        )
        val minThreshold = lowStockItem.minThreshold
        val currentQty = lowStockItem.quantity

        // Case 1: Low stock, 0 incoming -> Needs purchase
        val incomingZero = 0
        val isNeedsPurchase1 = currentQty < minThreshold && (currentQty + incomingZero < minThreshold)
        val isOnTheWayCovered1 = currentQty < minThreshold && (currentQty + incomingZero >= minThreshold)
        assertTrue(isNeedsPurchase1)
        assertFalse(isOnTheWayCovered1)

        // Case 2: Low stock (3 units), 4 units on the way (total 7 < 10) -> Still Needs Purchase
        val incomingPartial = 4
        val isNeedsPurchase2 = currentQty < minThreshold && (currentQty + incomingPartial < minThreshold)
        val isOnTheWayCovered2 = currentQty < minThreshold && (currentQty + incomingPartial >= minThreshold)
        assertTrue(isNeedsPurchase2)
        assertFalse(isOnTheWayCovered2)

        // Case 3: Low stock (3 units), 15 units on the way in Purchase Order (total 18 >= 10) -> Covered, No extra purchase needed!
        val incomingCovered = 15
        val isNeedsPurchase3 = currentQty < minThreshold && (currentQty + incomingCovered < minThreshold)
        val isOnTheWayCovered3 = currentQty < minThreshold && (currentQty + incomingCovered >= minThreshold)
        assertFalse(isNeedsPurchase3)
        assertTrue(isOnTheWayCovered3)
    }
}

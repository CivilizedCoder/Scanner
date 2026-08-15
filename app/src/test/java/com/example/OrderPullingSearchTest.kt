package com.example

import com.example.data.model.InventoryItem
import com.example.util.ItemSearchMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPullingSearchTest {

    private val sampleItems = listOf(
        InventoryItem(
            id = 1L,
            commonName = "Hydraulic Hose 1/2 inch",
            oldCode = "HOSE-HYD-01",
            newCode = "8901001001",
            quantity = 25,
            location = "Aisle 3, Shelf B2",
            category = "Hydraulics"
        ),
        InventoryItem(
            id = 2L,
            commonName = "Hex Bolt M10 x 50mm",
            oldCode = "BLT-HEX-M10",
            newCode = "8901001002",
            quantity = 150,
            location = "Aisle 1, Bin 04",
            category = "Fasteners"
        ),
        InventoryItem(
            id = 3L,
            commonName = "Hex Nut M10 Stainless",
            oldCode = "NUT-HEX-M10",
            newCode = "8901001003",
            quantity = 200,
            location = "Aisle 1, Bin 05",
            category = "Fasteners"
        ),
        InventoryItem(
            id = 4L,
            commonName = "Nitrile Heavy Duty Gloves (L)",
            oldCode = "GLV-NIT-L",
            newCode = "8901001004",
            quantity = 40,
            location = "Aisle 4, Shelf A1",
            category = "Safety Equipment"
        )
    )

    @Test
    fun testExactOldCodeMatch() {
        val results = ItemSearchMatcher.search("HOSE-HYD-01", sampleItems)
        assertTrue(results.isNotEmpty())
        assertEquals("Hydraulic Hose 1/2 inch", results.first().item.commonName)
        assertTrue(results.first().matchReason.contains("Old Code"))
    }

    @Test
    fun testMultiTokenSearch() {
        val results = ItemSearchMatcher.search("m10 bolt", sampleItems)
        assertTrue(results.isNotEmpty())
        assertEquals("Hex Bolt M10 x 50mm", results.first().item.commonName)
    }

    @Test
    fun testTypoFuzzySearch() {
        // "hidraulic" typo for Hydraulic
        val results = ItemSearchMatcher.search("hidraulic", sampleItems)
        assertTrue(results.isNotEmpty())
        assertEquals("Hydraulic Hose 1/2 inch", results.first().item.commonName)
        assertTrue(results.first().matchReason.contains("Typo Match"))
    }

    @Test
    fun testSubstituteRecommendation() {
        val target = sampleItems[1] // Hex Bolt (Fasteners)
        val substitutes = ItemSearchMatcher.findSubstitutes(target, sampleItems)
        assertTrue(substitutes.isNotEmpty())
        // Hex Nut in Fasteners category should be suggested as substitute
        assertEquals("Hex Nut M10 Stainless", substitutes.first().item.commonName)
        assertTrue(substitutes.first().matchReason.contains("Same Category"))
    }
}

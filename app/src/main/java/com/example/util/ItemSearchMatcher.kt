package com.example.util

import com.example.data.model.InventoryItem
import kotlin.math.max
import kotlin.math.min

data class ItemSearchResult(
    val item: InventoryItem,
    val score: Int,
    val matchReason: String,
    val matchedField: String
)

data class SubstituteSuggestion(
    val item: InventoryItem,
    val similarityScore: Int,
    val matchReason: String
)

object ItemSearchMatcher {

    /**
     * Robust search over a list of items using exact, prefix, multi-token, and fuzzy matching.
     */
    fun search(query: String, items: List<InventoryItem>): List<ItemSearchResult> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            return items.map {
                ItemSearchResult(
                    item = it,
                    score = 0,
                    matchReason = "Catalog Listing",
                    matchedField = "Default"
                )
            }
        }

        val tokens = cleanQuery.split(Regex("[\\s,._\\-/]+")).filter { it.isNotBlank() }

        val results = mutableListOf<ItemSearchResult>()

        for (item in items) {
            val nameLower = item.commonName.lowercase()
            val oldCodeLower = item.oldCode.lowercase()
            val newCodeLower = item.newCode.lowercase()
            val categoryLower = item.category.lowercase()
            val locationLower = item.location.lowercase()

            var score = 0
            var reason = ""
            var field = ""

            // 1. Exact Barcode / Code Match (Highest Priority)
            if (newCodeLower == cleanQuery || oldCodeLower == cleanQuery) {
                score += 1000
                reason = if (newCodeLower == cleanQuery) "Exact Barcode Match (${item.newCode})" else "Exact Old Code Match (${item.oldCode})"
                field = "Code"
            }
            // 2. Exact Name Match
            else if (nameLower == cleanQuery) {
                score += 900
                reason = "Exact Name Match"
                field = "Name"
            }
            // 3. Name Starts With Query
            else if (nameLower.startsWith(cleanQuery)) {
                score += 600
                reason = "Name Starts With Query"
                field = "Name"
            }
            // 4. Old Code Starts With Query
            else if (oldCodeLower.startsWith(cleanQuery)) {
                score += 550
                reason = "Old Code Starts With '${item.oldCode}'"
                field = "OldCode"
            }
            // 5. Name Contains Full Query Substring
            else if (nameLower.contains(cleanQuery)) {
                score += 400
                reason = "Matched Name Keyword"
                field = "Name"
            }
            // 6. Old Code Contains Query Substring
            else if (oldCodeLower.contains(cleanQuery)) {
                score += 350
                reason = "Matched Old Code (${item.oldCode})"
                field = "OldCode"
            }
            // 7. Category or Location Contains Query
            else if (categoryLower.contains(cleanQuery)) {
                score += 300
                reason = "Matched Category (${item.category})"
                field = "Category"
            } else if (locationLower.contains(cleanQuery)) {
                score += 250
                reason = "Matched Warehouse Location (${item.location})"
                field = "Location"
            }

            // 8. Multi-token Matching across all attributes (e.g. "m10 bolt", "gloves nitrile aisle 2")
            if (tokens.size > 1) {
                val combinedText = "$nameLower $oldCodeLower $newCodeLower $categoryLower $locationLower"
                val allTokensMatch = tokens.all { token -> combinedText.contains(token) }
                if (allTokensMatch) {
                    val tokenBonus = tokens.size * 60
                    score += 200 + tokenBonus
                    if (reason.isEmpty()) {
                        reason = "All ${tokens.size} search terms matched"
                        field = "MultiField"
                    }
                } else {
                    val matchCount = tokens.count { token -> combinedText.contains(token) }
                    if (matchCount > 0 && matchCount >= (tokens.size + 1) / 2) {
                        score += matchCount * 40
                        if (reason.isEmpty()) {
                            reason = "$matchCount of ${tokens.size} search terms matched"
                            field = "PartialTerms"
                        }
                    }
                }
            }

            // 9. Fuzzy matching for typos (e.g. "hidraulic" -> "Hydraulic", "screwdriwer" -> "Screwdriver")
            if (score == 0 && cleanQuery.length >= 3) {
                val itemWords = nameLower.split(Regex("[\\s,._\\-/]+")).filter { it.length >= 3 }
                var bestSim = 0.0
                var bestWord = ""

                for (word in itemWords) {
                    val sim = calculateStringSimilarity(cleanQuery, word)
                    if (sim > bestSim) {
                        bestSim = sim
                        bestWord = word
                    }
                }

                // Also check whole name similarity
                val fullNameSim = calculateStringSimilarity(cleanQuery, nameLower)
                val finalSim = max(bestSim, fullNameSim)

                if (finalSim >= 0.70) {
                    val fuzzyScore = (finalSim * 150).toInt()
                    score += fuzzyScore
                    val percentage = (finalSim * 100).toInt()
                    reason = "Typo Match: ~$percentage% similar to '${bestWord.ifEmpty { item.commonName }}'"
                    field = "Fuzzy"
                }
            }

            if (score > 0) {
                // In-stock bonus to rank available items slightly higher on ties
                val stockBonus = if (item.quantity > 0) 5 else 0
                results.add(
                    ItemSearchResult(
                        item = item,
                        score = score + stockBonus,
                        matchReason = reason,
                        matchedField = field
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * Finds intelligent substitutes for an item based on category, naming tokens, and stock availability.
     */
    fun findSubstitutes(
        targetItem: InventoryItem,
        allItems: List<InventoryItem>,
        limit: Int = 15
    ): List<SubstituteSuggestion> {
        val targetTokens = targetItem.commonName.lowercase()
            .split(Regex("[\\s,._\\-/]+"))
            .filter { it.length > 2 }
            .toSet()

        val candidates = allItems.filter { it.id != targetItem.id }

        val suggestions = mutableListOf<SubstituteSuggestion>()

        for (candidate in candidates) {
            var score = 0
            val reasons = mutableListOf<String>()

            // Same Category
            val sameCategory = candidate.category.equals(targetItem.category, ignoreCase = true)
            if (sameCategory) {
                score += 50
                reasons.add("Same Category (${candidate.category})")
            }

            // Word overlap in name (e.g. "Gloves", "Bolt", "Hose", "10mm", "Valve")
            val candidateTokens = candidate.commonName.lowercase()
                .split(Regex("[\\s,._\\-/]+"))
                .filter { it.length > 2 }
                .toSet()

            val overlap = targetTokens.intersect(candidateTokens)
            if (overlap.isNotEmpty()) {
                score += overlap.size * 30
                reasons.add("Shared specs: ${overlap.joinToString(", ")}")
            }

            // In Stock Priority
            if (candidate.quantity > 0) {
                score += 20
                if (candidate.quantity >= targetItem.quantity) {
                    score += 10
                }
            } else {
                score -= 30 // Penalize 0-stock items
            }

            // Only suggest if there's meaningful similarity
            if (score > 25) {
                suggestions.add(
                    SubstituteSuggestion(
                        item = candidate,
                        similarityScore = score,
                        matchReason = reasons.joinToString(" • ")
                    )
                )
            }
        }

        return suggestions.sortedByDescending { it.similarityScore }.take(limit)
    }

    /**
     * Levenshtein Distance based String Similarity (0.0 to 1.0)
     */
    fun calculateStringSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val maxLen = max(len1, len2)
        val distance = levenshteinDistance(s1, s2)
        return (maxLen - distance).toDouble() / maxLen.toDouble()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

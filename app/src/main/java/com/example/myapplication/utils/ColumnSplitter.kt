package com.example.myapplication.utils

import com.example.myapplication.models.PresetColumn
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset

object ColumnSplitter {

    fun splitMergedColumns(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset?) {
        if (rawRowsList.isEmpty() || activePreset == null) return
        val columns = activePreset.columns
        if (columns.size < 2) return

        val totalRows = rawRowsList.size

        for (cIndex in 0 until (columns.size - 1)) {
            val currentCol = columns[cIndex]
            val nextCol = columns[cIndex + 1]

            // Find rows where currentCol has space-delimited text
            val mergedRowIndices = mutableListOf<Int>()
            for (rIndex in rawRowsList.indices) {
                val row = rawRowsList[rIndex]
                val cellVal = row.getCell(currentCol.role).ifBlank { row.getCell(currentCol.sourceHeader) }.trim()
                if (cellVal.contains(" ")) {
                    val parts = cellVal.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        mergedRowIndices.add(rIndex)
                    }
                }
            }

            if (mergedRowIndices.isEmpty()) continue

            val ratio = mergedRowIndices.size.toDouble() / totalRows.toDouble()
            val isMajority = ratio >= 0.50

            for (rIndex in mergedRowIndices) {
                val row = rawRowsList[rIndex]
                val cellVal = row.getCell(currentCol.role).ifBlank { row.getCell(currentCol.sourceHeader) }.trim()
                val parts = cellVal.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.size < 2) continue

                val part1 = parts[0]
                val part2 = parts.drop(1).joinToString(" ")

                val shouldSplit = if (isMajority) {
                    true
                } else {
                    matchesColumnFormat(part1, currentCol) && matchesColumnFormat(part2, nextCol)
                }

                if (shouldSplit) {
                    // Collect current row values for columns cIndex to end
                    val oldVals = (cIndex until columns.size).map { k ->
                        val col = columns[k]
                        row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) }
                    }

                    // Assign part1 to cIndex
                    row.setCell(currentCol.role, part1)
                    row.setCell(currentCol.sourceHeader, part1)

                    // Assign part2 to cIndex + 1
                    row.setCell(nextCol.role, part2)
                    row.setCell(nextCol.sourceHeader, part2)

                    // Shift subsequent values 1 column to the right
                    for (k in (cIndex + 2) until columns.size) {
                        val shiftedVal = oldVals[k - (cIndex + 1)]
                        val targetCol = columns[k]
                        row.setCell(targetCol.role, shiftedVal)
                        row.setCell(targetCol.sourceHeader, shiftedVal)
                    }
                }
            }
        }
    }

    fun matchesColumnFormat(token: String, col: PresetColumn): Boolean {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return false

        val rule = col.normalizationRule ?: col.role

        return when {
            rule == "date_dd_mm" || col.role == "date" -> {
                cleanToken.contains("/") || cleanToken.contains(".") || cleanToken.contains("-") ||
                        cleanToken.filter { it.isDigit() }.length in listOf(4, 6, 8)
            }

            rule == "time_hh_mm" || col.role in listOf("start_time", "end_time", "load_start", "cnc_start", "cnc_end", "unload_end") -> {
                val digits = cleanToken.filter { it.isDigit() }
                cleanToken.contains(":") || (digits.length in 3..4 && cleanToken.replace(".", "").filter { it.isDigit() }.length in 3..4)
            }

            rule == "project_code" || col.role == "project" -> {
                val upper = cleanToken.uppercase()
                upper.startsWith("R") || upper.startsWith("P") || cleanToken.toIntOrNull() != null
            }

            rule == "numeric" || col.role in listOf("thickness", "width", "incoming_m", "outgoing_m", "amount", "long_width", "long_count", "cross_width", "cross_count") -> {
                cleanToken.replace(",", ".").toDoubleOrNull() != null
            }

            rule == "panel_enum" || col.role == "panel" -> {
                val upper = cleanToken.uppercase()
                upper.contains("V/V") || upper.contains("V/N") || upper.contains("N/N") || upper.contains("VIZ")
            }

            rule == "layer_code" || col.role == "layer" -> {
                val upper = cleanToken.uppercase()
                upper.contains("VIZ") || upper.contains("NEV") || upper.contains("NEU")
            }

            else -> {
                cleanToken.isNotBlank()
            }
        }
    }
}

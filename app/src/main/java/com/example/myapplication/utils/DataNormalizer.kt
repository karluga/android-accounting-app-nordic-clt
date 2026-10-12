package com.example.myapplication.utils

import com.example.myapplication.models.PresetColumn
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import java.util.Locale

object DataNormalizer {

    fun normalizeRows(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset?) {
        if (rawRowsList.isEmpty() || activePreset == null) return

        // 0. Pre-processing: Split merged columns if cells merged by accident (< 50% strict format vs >= 50% majority)
        splitMergedColumns(rawRowsList, activePreset)

        var activeDate = ""
        var activeOperator = ""
        val maxRatio = activePreset.maxOutgoingRatio

        for (row in rawRowsList) {
            // 1. Forward-filling Date
            val dateCol = activePreset.columns.firstOrNull { it.role == "date" || it.sourceHeader.contains("Dat", true) }
            if (dateCol != null) {
                val currentDat = row.getCell(dateCol.role).ifBlank { row.getCell(dateCol.sourceHeader) }
                if (currentDat.isNotBlank() && currentDat != "00/01/2026" && currentDat != "0") {
                    activeDate = normalizeDate(currentDat)
                    row.setCell(dateCol.role, activeDate)
                    row.setCell(dateCol.sourceHeader, activeDate)
                } else if (activeDate.isNotBlank()) {
                    row.setCell(dateCol.role, activeDate)
                    row.setCell(dateCol.sourceHeader, activeDate)
                }
            }

            // 2. Forward-filling Operator
            val opCol = activePreset.columns.firstOrNull { it.role == "operator" || it.sourceHeader.contains("Operat", true) }
            if (opCol != null) {
                val currentOp = row.getCell(opCol.role).ifBlank { row.getCell(opCol.sourceHeader) }
                if (currentOp.isNotBlank()) {
                    activeOperator = normalizeOperator(currentOp, opCol.enumValues)
                    row.setCell(opCol.role, activeOperator)
                    row.setCell(opCol.sourceHeader, activeOperator)
                } else if (activeOperator.isNotBlank()) {
                    row.setCell(opCol.role, activeOperator)
                    row.setCell(opCol.sourceHeader, activeOperator)
                }
            }

            // 3. Process each column based on its role/normalization rule
            for (col in activePreset.columns) {
                val valRaw = row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) }
                if (valRaw.isBlank()) continue

                val normalized = when (col.normalizationRule) {
                    "date_dd_mm" -> normalizeDate(valRaw)
                    "time_hh_mm" -> normalizeTime(valRaw)
                    "operator_whitelist_diefenbacher", "operator" -> normalizeOperator(valRaw, col.enumValues)
                    "project_code" -> normalizeProjectCode(valRaw)
                    "panel_enum" -> normalizePanel(valRaw, col.enumValues)
                    "layer_code" -> normalizeLayer(valRaw, col.enumValues)
                    "numeric" -> normalizeNumeric(valRaw)
                    else -> {
                        when (col.role) {
                            "date" -> normalizeDate(valRaw)
                            "operator" -> normalizeOperator(valRaw, col.enumValues)
                            "project" -> normalizeProjectCode(valRaw)
                            "panel" -> normalizePanel(valRaw, col.enumValues)
                            "layer" -> normalizeLayer(valRaw, col.enumValues)
                            "thickness", "width", "incoming_m", "outgoing_m", "amount", "long_width", "long_count", "cross_width", "cross_count" -> normalizeNumeric(valRaw)
                            else -> valRaw
                        }
                    }
                }

                row.setCell(col.role, normalized)
                row.setCell(col.sourceHeader, normalized)
            }

            // 4. Diefenbacher Specific Rule (Section 9): outgoing_m / amount > maxRatio -> clear outgoing_m
            if (activePreset.key.contains("diefenbacher", true) || activePreset.taskName.contains("Audzētājs", true)) {
                val outgoingStr = row.getCell("outgoing_m").ifBlank { row.getCell("Izejošie metri") }
                val amountStr = row.getCell("amount").ifBlank { row.getCell("gb.") }
                val outgoingVal = outgoingStr.toDoubleOrNull() ?: 0.0
                val amountVal = amountStr.toDoubleOrNull() ?: 0.0

                if (amountVal > 0.0 && (outgoingVal / amountVal) > maxRatio) {
                    row.setCell("outgoing_m", "")
                    row.setCell("Izejošie metri", "")
                }
            }
        }
    }

    fun normalizeDate(raw: String): String {
        val clean = raw.replace(Regex("[^0-9./-]"), "").trim()
        if (clean.isBlank() || clean == "0" || clean.startsWith("00")) return "03/07/2026"

        if (clean.contains("/") || clean.contains(".") || clean.contains("-")) {
            val parts = clean.split(Regex("[./-]"))
            if (parts.size >= 2) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = if (parts.size >= 3 && parts[2].length == 4) parts[2] else "2026"
                return "$day/$month/$year"
            }
        }

        val digits = clean.filter { it.isDigit() }
        when (digits.length) {
            4 -> {
                val day = digits.substring(0, 2)
                val month = digits.substring(2, 4)
                return "$day/$month/2026"
            }
            6 -> {
                val day = digits.substring(0, 2)
                val month = digits.substring(2, 4)
                val yr = digits.substring(4, 6)
                val year = if (yr.toIntOrNull() ?: 0 > 50) "19$yr" else "20$yr"
                return "$day/$month/$year"
            }
            8 -> {
                val day = digits.substring(0, 2)
                val month = digits.substring(2, 4)
                val year = digits.substring(4, 8)
                return "$day/$month/$year"
            }
        }

        return raw.ifBlank { "03/07/2026" }
    }

    fun normalizeTime(raw: String): String {
        var clean = raw.replace(Regex("[^0-9:]"), "").trim()
        if (clean.contains(".")) {
            clean = clean.substringBefore(".")
        }
        val digits = clean.filter { it.isDigit() }

        var hourStr = ""
        var minStr = ""

        if (clean.contains(":")) {
            val parts = clean.split(":")
            var h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (h > 23 && (h in 80..89 || h == 84)) {
                h = 8
            }
            hourStr = h.toString().padStart(2, '0')
            minStr = m.toString().padStart(2, '0')
            return "$hourStr:$minStr"
        }

        when (digits.length) {
            4 -> {
                var h = digits.substring(0, 2).toIntOrNull() ?: 0
                val m = digits.substring(2, 4).toIntOrNull() ?: 0
                if (h > 23 && (h in 80..89 || h == 84)) {
                    h = 8
                }
                hourStr = h.toString().padStart(2, '0')
                minStr = m.toString().padStart(2, '0')
                return "$hourStr:$minStr"
            }
            3 -> {
                val h = digits.substring(0, 1).toIntOrNull() ?: 0
                val m = digits.substring(1, 3).toIntOrNull() ?: 0
                hourStr = h.toString().padStart(2, '0')
                minStr = m.toString().padStart(2, '0')
                return "$hourStr:$minStr"
            }
            2 -> {
                return "${digits}:00"
            }
        }

        return raw
    }

    fun normalizeOperator(raw: String, enumValues: List<String>): String {
        val upper = raw.uppercase().trim()
        if (enumValues.isNotEmpty()) {
            val match = enumValues.firstOrNull { it.uppercase() == upper || upper.contains(it.uppercase()) }
            if (match != null) return match
        }
        return upper
    }

    fun normalizeProjectCode(raw: String): String {
        // 1. Remove any dots from the project code (e.g., R.02 -> R02)
        var clean = raw.trim().uppercase().replace(".", "")

        val rIndex = clean.indexOf('R')
        if (rIndex != -1) {
            val prefix = clean.substring(0, rIndex + 1)
            val suffix = clean.substring(rIndex + 1)
                .replace('O', '0') // 2. Letter 'O' after 'R' in any position is replaced with '0'
                .replace('Z', '2') // 3. Letter 'Z' after 'R' in any position is replaced with '2'
            clean = prefix + suffix
        }

        if (clean.toIntOrNull() != null) {
            return "R" + clean.padStart(2, '0')
        }
        if (!clean.startsWith("R") && clean.isNotEmpty()) {
            if (clean.all { it.isDigit() || it == '-' }) {
                return "R$clean"
            }
        }
        return clean
    }

    fun normalizePanel(raw: String, enumValues: List<String>): String {
        val upper = raw.uppercase().trim()
        if (upper.contains("V/V")) return "V/V"
        if (upper.contains("V/N") || upper.contains("V/NEU")) return "V/N"
        if (upper.contains("N/N")) return "N/N"
        if (upper.startsWith("V") && upper.endsWith("Z")) return "VIZ"
        if (enumValues.isNotEmpty()) {
            val match = enumValues.firstOrNull { it.uppercase() == upper || upper.contains(it.uppercase()) }
            if (match != null) return match
        }
        return upper
    }

    fun normalizeLayer(raw: String, enumValues: List<String>): String {
        val upper = raw.uppercase().trim()
        if (upper == "V" || upper.contains("VIZ") || (upper.startsWith("V") && upper.endsWith("Z"))) return "VIZ"
        if (upper == "N" || upper.contains("NEU") || upper.contains("NEV") || (upper.startsWith("N") && upper.endsWith("V") && upper.contains("E"))) return "NEV"
        if (enumValues.isNotEmpty()) {
            val match = enumValues.firstOrNull { it.uppercase() == upper || upper.contains(it.uppercase()) }
            if (match != null) return match
        }
        return upper
    }

    fun normalizeNumeric(raw: String): String {
        val clean = raw.replace(Regex("[^0-9.]"), "").trim()
        return clean.ifBlank { raw }
    }

    fun validateRequiredColumns(rawRowsList: List<RawScannedRow>, activePreset: TablePreset?): String? {
        if (activePreset == null || rawRowsList.isEmpty()) return null

        val requiredColumns = activePreset.columns.filter { !it.nullable }
        if (requiredColumns.isEmpty()) return null

        rawRowsList.forEachIndexed { rowIndex, row ->
            for (col in requiredColumns) {
                val valCell = row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) }
                if (valCell.isBlank()) {
                    return "Kļūda rindā ${rowIndex + 1}: Obligāts lauks '${col.sourceHeader}' (${col.role}) ir tukšs! Lūdzu, aizpildiet pirms ģenerēšanas."
                }
            }
        }
        return null
    }

    fun isForwardFillAllowed(col: PresetColumn): Boolean {
        if (!col.forwardFill) return false
        if (col.normalizationRule == "time_hh_mm") return false
        val rLower = col.role.lowercase(Locale.ROOT)
        if (rLower in listOf("start_time", "end_time", "load_start", "cnc_start", "cnc_end", "unload_end", "sim_duration")) {
            return false
        }
        return true
    }

    fun splitMergedColumns(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset?) {
        ColumnSplitter.splitMergedColumns(rawRowsList, activePreset)
    }

    fun matchesColumnFormat(token: String, col: PresetColumn): Boolean {
        return ColumnSplitter.matchesColumnFormat(token, col)
    }
}

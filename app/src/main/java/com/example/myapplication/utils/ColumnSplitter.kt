package com.example.myapplication.utils

import com.example.myapplication.models.PresetColumn
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset

object ColumnSplitter {

    /**
     * Section 10.A: Individual Cell Token Splitting (`split_merged_cell_token`)
     * Unpacks merged tokens inside a single cell into a list of individual tokens.
     */
    fun splitMergedCellToken(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return listOf(text)

        // Pattern 1: Operator acronym followed by project code (e.g., "ABA R30", "DSA R02")
        val operatorRegex = Regex("^(ABA|RGO|RVE|DSA|AGU|VŠ|VŠĻ|GPU|GR)\\s+([rR]?-?\\d+\\w*)$", RegexOption.IGNORE_CASE)
        val opMatch = operatorRegex.find(trimmed)
        if (opMatch != null) {
            return listOf(opMatch.groupValues[1], opMatch.groupValues[2])
        }

        // Pattern 2: Master Panel ID (5-8 digits) followed by project code/number (e.g., "2015002 R-31", "2017000 31")
        val masterProjRegex = Regex("^(\\d{5,8})\\s+([rR]?-?\\d+\\w*)$", RegexOption.IGNORE_CASE)
        val mpMatch = masterProjRegex.find(trimmed)
        if (mpMatch != null) {
            return listOf(mpMatch.groupValues[1], mpMatch.groupValues[2])
        }

        // Pattern 3: General Alphanumeric Merged Pair (1-8 chars + project code or 5-8 digit ID)
        val generalPairRegex = Regex("^([a-zA-Z0-9]{1,8})\\s+([rR]-?\\d+\\w*|\\d{5,8})$")
        val genMatch = generalPairRegex.find(trimmed)
        if (genMatch != null) {
            return listOf(genMatch.groupValues[1], genMatch.groupValues[2])
        }

        return listOf(text)
    }

    /**
     * Section 10.B & 11: Main entry point for semantic column alignment and splitting.
     */
    fun splitMergedColumns(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset?) {
        if (rawRowsList.isEmpty() || activePreset == null) return

        val isDiefenbacher = activePreset.key.contains("diefenbacher", ignoreCase = true) ||
                activePreset.taskName.contains("Audzē", ignoreCase = true)

        if (isDiefenbacher) {
            alignDiefenbacherTokens(rawRowsList, activePreset)
        } else {
            alignVacuumpressTokens(rawRowsList, activePreset)
        }
    }

    /**
     * Section 10.B: Diefenbacher Row Normalization & Semantic Field Alignment
     */
    fun alignDiefenbacherTokens(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset) {
        val columns = activePreset.columns
        if (columns.size < 2) return

        // First pass: realign misaligned rows where leading columns were omitted
        realignMisalignedRows(rawRowsList, activePreset)

        for (row in rawRowsList) {
            // 1. Merged Project & Start Time Splitting (e.g., "R30 08:50" -> project="R30", start_time="08:50")
            val projVal = row.getCell("project").ifBlank { row.getCell("Proj.Nr.") }
            if (projVal.contains(" ")) {
                val projTimeRegex = Regex("^([rR]\\.?[0-9]{1,4}(?:\\s+[IVX]+)?(?:[a-zA-Z0-9_\\-]*))\\s+(\\d{1,2}[:.\\-]\\d{2})$")
                val match = projTimeRegex.find(projVal.trim())
                if (match != null) {
                    val cleanProj = match.groupValues[1].replace(".", "")
                    val cleanTime = match.groupValues[2].replace(".", ":").replace("-", ":")

                    row.setCell("project", cleanProj)
                    row.setCell("Proj.Nr.", cleanProj)

                    val currentStartTime = row.getCell("start_time").ifBlank { row.getCell("Apstrādes sākšanas laiks") }
                    row.setCell("start_time", cleanTime)
                    row.setCell("Apstrādes sākšanas laiks", cleanTime)

                    // If start_time previously held thickness ("47"), shift values right
                    if (currentStartTime.isNotBlank() && currentStartTime.replace(",", ".").toDoubleOrNull() != null) {
                        var tempVal = currentStartTime
                        val shiftRoles = listOf("thickness", "width", "incoming_m", "outgoing_m", "amount")
                        val shiftHeaders = listOf("Biez.", "Plat.", "Ienākošie metri", "Izejošie metri", "gb.")

                        for (i in shiftRoles.indices) {
                            val r = shiftRoles[i]
                            val h = shiftHeaders[i]
                            val oldCellVal = row.getCell(r).ifBlank { row.getCell(h) }
                            row.setCell(r, tempVal)
                            row.setCell(h, tempVal)
                            tempVal = oldCellVal
                            if (tempVal.isBlank()) break
                        }
                    }
                }
            }

            // 2. Merged Panel Quality & Layer Enum Splitting (e.g. "V/V N" -> panel="V/V", layer="N")
            for (role in listOf("outgoing_m", "width", "panel")) {
                val cellVal = row.getCell(role)
                if (cellVal.isBlank()) continue
                val enumPairRegex = Regex("\\b(V/V|V/N|N/N)\\s+([a-zA-Z]{1,4})\\b", RegexOption.IGNORE_CASE)
                val match = enumPairRegex.find(cellVal.trim())
                if (match != null) {
                    val panelVal = match.groupValues[1].uppercase()
                    val layerVal = match.groupValues[2].uppercase().replace("VIZ", "").trim().ifBlank { "VIZ" }

                    row.setCell("panel", panelVal)
                    row.setCell("Panelis", panelVal)
                    row.setCell("layer", layerVal)
                    row.setCell("Slānis", layerVal)

                    if (role != "panel") {
                        row.setCell(role, "")
                    }
                }
            }

            // 3. Master Panel ID Extraction & Normalization
            val masterRegex = Regex("(20\\d{5}|\\d{7})(?:[\\s/(\\[]+(\\d{1,2})\\)?)*")
            for (role in listOf("comments", "layer", "end_time", "panel")) {
                val valStr = row.getCell(role)
                if (valStr.isBlank()) continue
                val match = masterRegex.find(valStr)
                if (match != null) {
                    val masterId = match.groupValues[1]
                    val suffix = match.groupValues.getOrNull(2) ?: ""
                    val normalizedMaster = if (suffix.isNotBlank()) "$masterId(OK)" else if (valStr.contains("(OK)")) "$masterId(OK)" else masterId

                    row.setCell("comments", normalizedMaster)
                    row.setCell("Dīkstāve un komentāri", normalizedMaster)

                    if (role != "comments") {
                        val cleanedVal = valStr.replace(match.value, "").trim()
                        row.setCell(role, cleanedVal)
                    }
                    break
                }
            }

            // 4. Diefenbacher Numeric Field Column Shifting
            val incoming = row.getCell("incoming_m").ifBlank { row.getCell("Ienākošie metri") }
            val outgoing = row.getCell("outgoing_m").ifBlank { row.getCell("Izejošie metri") }
            val amount = row.getCell("amount").ifBlank { row.getCell("gb.") }

            if (incoming.isNotBlank() && outgoing.isNotBlank() && amount.isBlank()) {
                val incNum = incoming.replace(",", ".").toDoubleOrNull()
                val outNum = outgoing.replace(",", ".").toDoubleOrNull()

                if (incNum != null && outNum != null) {
                    row.setCell("amount", outgoing)
                    row.setCell("gb.", outgoing)

                    row.setCell("outgoing_m", incoming)
                    row.setCell("Izejošie metri", incoming)

                    row.setCell("incoming_m", "")
                    row.setCell("Ienākošie metri", "")
                }
            }
        }
    }

    /**
     * Section 11.A: Positional Token Alignment & Token Flattening (Vakuumprese & CNC)
     */
    fun alignVacuumpressTokens(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset) {
        val columns = activePreset.columns
        if (columns.isEmpty()) return

        for (row in rawRowsList) {
            // Expand merged tokens cell-by-cell
            val rawTokens = mutableListOf<String>()
            for (col in columns) {
                val cellVal = row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) }
                if (cellVal.isNotBlank()) {
                    rawTokens.addAll(splitMergedCellToken(cellVal))
                }
            }

            // Clear row and map tokens 1-to-1 positionally
            columns.forEach { col ->
                row.setCell(col.role, "")
                row.setCell(col.sourceHeader, "")
            }

            for (idx in columns.indices) {
                val tok = rawTokens.getOrElse(idx) { "" }
                val col = columns[idx]
                row.setCell(col.role, tok)
                row.setCell(col.sourceHeader, tok)
            }
        }
    }

    /**
     * Helper: Realign misaligned rows where leading columns (like Date/Operator) were omitted by OCR
     */
    fun realignMisalignedRows(rawRowsList: MutableList<RawScannedRow>, activePreset: TablePreset) {
        val columns = activePreset.columns
        if (columns.size < 2) return

        val dateCol = columns.firstOrNull { it.role == "date" }
        val projCol = columns.firstOrNull { it.role == "project" } ?: return
        val projColIndex = columns.indexOf(projCol)

        val panelColIndex = columns.indexOfFirst { it.role == "panel" }
        val commentsColIndex = columns.indexOfFirst { it.role == "comments" }

        for (row in rawRowsList) {
            val rawTokens = columns.map { col ->
                row.getCell(col.role).ifBlank { row.getCell(col.sourceHeader) }
            }.filter { it.isNotBlank() }

            if (rawTokens.isEmpty()) continue

            val firstTok = rawTokens[0]

            // Check if first token in row is actually a project_code (e.g. "R30") placed in date/operator column
            if (dateCol != null && matchesColumnFormat(firstTok, projCol) && !matchesColumnFormat(firstTok, dateCol)) {
                // Clear all cells in row and realign starting at project column
                columns.forEach { col ->
                    row.setCell(col.role, "")
                    row.setCell(col.sourceHeader, "")
                }

                var tokIdx = 0
                var colIdx = projColIndex

                while (tokIdx < rawTokens.size && colIdx < columns.size) {
                    val tok = rawTokens[tokIdx]
                    var targetCol = columns[colIdx]

                    // If token matches panel enum (e.g. "V/N", "V/V", "N/N") but targetCol is not panel, jump to panel column
                    if (panelColIndex != -1 && colIdx < panelColIndex && (tok.contains("V/V") || tok.contains("V/N") || tok.contains("N/N"))) {
                        colIdx = panelColIndex
                        targetCol = columns[colIdx]
                    }

                    // If targetCol is a time column (HH:MM), but tok is numeric (like "47"), skip the time column
                    if ((targetCol.normalizationRule == "time_hh_mm" || targetCol.role in listOf("start_time", "end_time", "load_start", "cnc_start", "cnc_end", "unload_end")) &&
                        !matchesColumnFormat(tok, targetCol)
                    ) {
                        colIdx++
                        continue
                    }

                    // Handle combined panel and layer tokens e.g. "V/N V" or "V/V N"
                    if (targetCol.role == "panel" && colIdx + 1 < columns.size && columns[colIdx + 1].role == "layer" && tok.contains(" ")) {
                        val parts = tok.split(Regex("\\s+")).filter { it.isNotBlank() }
                        row.setCell(targetCol.role, parts.getOrElse(0) { "" })
                        row.setCell(targetCol.sourceHeader, parts.getOrElse(0) { "" })
                        colIdx++

                        val layerCol = columns[colIdx]
                        row.setCell(layerCol.role, parts.getOrElse(1) { "" })
                        row.setCell(layerCol.sourceHeader, parts.getOrElse(1) { "" })
                        colIdx++
                        tokIdx++
                        continue
                    }

                    // If token looks like master/comments (e.g. 7-digit ID "2015000(04)"), jump to comments column if needed
                    if (commentsColIndex != -1 && colIdx < commentsColIndex && tok.length >= 7 && (tok.contains("(") || tok.all { it.isDigit() })) {
                        colIdx = commentsColIndex
                        targetCol = columns[colIdx]
                    }

                    row.setCell(targetCol.role, tok)
                    row.setCell(targetCol.sourceHeader, tok)
                    colIdx++
                    tokIdx++
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

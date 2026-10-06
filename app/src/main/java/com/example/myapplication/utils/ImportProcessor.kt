package com.example.myapplication.utils

import android.os.Environment
import com.example.myapplication.models.ExcelExportRow
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ImportProcessor {

    // Generate the 7-column export data and file name <date><production_step>.xlsx from verified raw rows
    fun generateExcelImportDataFromRaw(
        rawRowsList: List<RawScannedRow>,
        activePreset: TablePreset?,
        selectedProcess: String
    ): Pair<String, List<ExcelExportRow>> {
        val stepName = activePreset?.taskName ?: selectedProcess

        if (rawRowsList.isEmpty()) {
            val defaultDate = "03-07-2026"
            val sanitizedStep = sanitizeFileName(stepName)
            val fileName = "${defaultDate}_$sanitizedStep.xlsx"
            return Pair(fileName, emptyList())
        }

        // First row date for file name
        val firstDateRaw = rawRowsList.firstOrNull { it.getCell("date").isNotBlank() || it.getCell("Dat. 2026").isNotBlank() }
            ?.let { it.getCell("date").ifBlank { it.getCell("Dat. 2026") } } ?: "03/07/2026"

        val formattedDateForFile = firstDateRaw.replace("/", "-").replace(".", "-")
        val sanitizedStep = sanitizeFileName(stepName)
        val fileName = "${formattedDateForFile}_$sanitizedStep.xlsx"

        // Group raw rows by Date and Project
        val groupedMap = rawRowsList.groupBy { row ->
            val d = row.getCell("date").ifBlank { row.getCell("Dat. 2026") }.ifBlank { firstDateRaw }
            val p = row.getCell("project").ifBlank { row.getCell("Proj.Nr.") }.ifBlank { row.getCell("Proj. Nr.") }.ifBlank { row.getCell("Projekta Nr.") }.ifBlank { "Projekts" }
            Pair(d, p)
        }

        val exportRows = mutableListOf<ExcelExportRow>()

        for ((groupKey, groupRows) in groupedMap) {
            val dateVal = groupKey.first
            val projectVal = groupKey.second

            var totalM3 = 0.0
            var okMasterCount = 0
            var isBrusas = false

            for (r in groupRows) {
                // Calculate m3 from outgoing_m * width * thickness / 1,000,000 or m3 field
                val outgoingM = r.getCell("outgoing_m").toDoubleOrNull() ?: r.getCell("Izejošie metri").toDoubleOrNull() ?: 0.0
                val width = r.getCell("width").toDoubleOrNull() ?: r.getCell("Plat.").toDoubleOrNull() ?: 0.0
                val thickness = r.getCell("thickness").toDoubleOrNull() ?: r.getCell("Biez.").toDoubleOrNull() ?: 0.0

                val rowCalculatedM3 = if (outgoingM > 0 && width > 0 && thickness > 0) {
                    (outgoingM * width * thickness) / 1000000.0
                } else {
                    r.getCell("m3").toDoubleOrNull() ?: 0.0
                }

                totalM3 += rowCalculatedM3

                // Diefenbacher / master check
                val comments = r.getCell("comments") + " " + r.getCell("Dīkstāve un komentāri") + " " + r.getCell("master") + " " + r.getCell("Mastera nr.")
                if (comments.contains("Brusas", ignoreCase = true)) {
                    isBrusas = true
                }
                if (comments.contains("(OK)", ignoreCase = true) || comments.contains("OK", ignoreCase = true)) {
                    okMasterCount++
                }
            }

            val m3Formatted = if (totalM3 > 0) String.format(Locale.US, "%.3f", totalM3) else "1.000"

            val mastersDisplay = when {
                isBrusas -> "Brusas"
                okMasterCount > 0 -> okMasterCount.toString()
                else -> {
                    val sumAmount = groupRows.sumOf { r ->
                        r.getCell("amount").toIntOrNull() ?: r.getCell("gb.").toIntOrNull() ?: r.getCell("master").toIntOrNull() ?: 0
                    }
                    if (sumAmount > 0) sumAmount.toString() else groupRows.size.toString()
                }
            }

            exportRows.add(
                ExcelExportRow(
                    taskName = stepName,
                    status = "Completed",
                    date = dateVal,
                    m3 = m3Formatted,
                    mastersCount = mastersDisplay,
                    relatedTo = projectVal,
                    type = "Ražošana"
                )
            )
        }

        return Pair(fileName, exportRows)
    }

    // Parse actual OCR.space response text and map line tokens to preset columns in rawRowsList
    fun parseAndApplyOcrToRawRows(
        response: String?,
        activePreset: TablePreset?,
        rawRowsList: MutableList<RawScannedRow>,
        currentPageNum: Int,
        rotationAngle: Float
    ): String {
        val rotationInfo = "Lapa $currentPageNum (Orientācija: ${rotationAngle.toInt()}°)"
        val jsonDebugInfo = if (!response.isNullOrBlank()) "\n\nRaw OCR JSON Response:\n$response" else ""

        if (response.isNullOrBlank()) {
            return "OCR.space atbilde ir tukša. [$rotationInfo]\nNav nolasīti dati no dokumenta."
        }

        try {
            val json = JSONObject(response)
            val isErrored = json.optBoolean("IsErroredOnProcessing", false)
            if (isErrored) {
                val errorMsg = json.optJSONArray("ErrorMessage")?.optString(0) ?: "Nezināma OCR kļūda"
                return "OCR.space Kļūda [$rotationInfo]: $errorMsg.$jsonDebugInfo"
            }

            val parsedResults = json.optJSONArray("ParsedResults")
            if (parsedResults == null || parsedResults.length() == 0) {
                return "OCR.space atbilde nesatur ParsedResults.$jsonDebugInfo"
            }

            val firstResult = parsedResults.getJSONObject(0)
            val parsedText = firstResult.optString("ParsedText", "")

            if (parsedText.isBlank()) {
                return "OCR.space nespēja nolasīt tekstu no dokumenta [$rotationInfo].$jsonDebugInfo"
            }

            val columns = activePreset?.columns ?: emptyList()
            val lines = parsedText.split("\r\n", "\n").map { it.trim() }.filter { it.isNotBlank() }

            var addedCount = 0

            for (line in lines) {
                // Filter out document header row so it doesn't get inserted as a data row
                val isHeaderRow = line.contains("Dat", ignoreCase = true) ||
                        line.contains("Operat", ignoreCase = true) ||
                        line.contains("Proj", ignoreCase = true) ||
                        line.contains("Biez", ignoreCase = true) ||
                        line.contains("Plat", ignoreCase = true) ||
                        line.contains("Master", ignoreCase = true) ||
                        line.contains("Apstrādes", ignoreCase = true) ||
                        line.contains("sākšanas", ignoreCase = true) ||
                        line.contains("Task", ignoreCase = true)

                if (isHeaderRow) {
                    continue // Skip document header row
                }

                // Split line by tabs or multiple spaces
                val tokens = line.split(Regex("\t|\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue

                val newRow = RawScannedRow()

                // Map tokens to active preset columns
                tokens.forEachIndexed { tokenIdx, tokenText ->
                    if (tokenIdx < columns.size) {
                        val col = columns[tokenIdx]
                        newRow.setCell(col.role, tokenText)
                        newRow.setCell(col.sourceHeader, tokenText)
                    }
                }

                rawRowsList.add(newRow)
                addedCount++
            }

            return if (addedCount > 0) {
                "OCR.space sekmīgi nolasīja $addedCount rindas [$rotationInfo]!\nDati ielādēti tabulā. Lūdzu, pārbaudiet un rediģējiet šūnas pirms ģenerēšanas.$jsonDebugInfo"
            } else {
                "OCR.space nolasīja tekstu, bet netika identificēta neviena datu rinda [$rotationInfo].$jsonDebugInfo"
            }

        } catch (e: Exception) {
            return "OCR atbildes parsēšanas kļūda (${e.localizedMessage}) [$rotationInfo].$jsonDebugInfo"
        }
    }

    fun saveExcelImportFile(fileName: String, exportRows: List<ExcelExportRow>): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)

            val builder = StringBuilder()
            builder.append("Task name\tStatus\tDatums\tSaražoti m3\tKopā masteri\tRelated to\tTips\n")

            for (row in exportRows) {
                builder.append("${row.taskName}\t${row.status}\t${row.date}\t${row.m3}\t${row.mastersCount}\t${row.relatedTo}\t${row.type}\n")
            }

            file.writeText(builder.toString(), Charsets.UTF_8)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeFileName(raw: String): String {
        return raw.replace(".", "").replace(" ", "_")
            .replace("ā", "a").replace("ē", "e").replace("ī", "i").replace("ū", "u")
            .replace("š", "s").replace("ģ", "g").replace("ķ", "k").replace("ļ", "l")
            .replace("ž", "z").replace("č", "c").replace("ņ", "n")
    }
}

package com.example.myapplication.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.myapplication.AppDestinations
import com.example.myapplication.models.ProductionRow
import com.example.myapplication.models.RawScannedRow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    // Current bottom navigation destination
    var currentDestination = mutableStateOf(
        try {
            AppDestinations.valueOf(savedStateHandle.get<String>("KEY_DESTINATION") ?: "IMPORT")
        } catch (_: Exception) {
            AppDestinations.IMPORT
        }
    )
        private set

    fun updateDestination(destination: AppDestinations) {
        currentDestination.value = destination
        savedStateHandle["KEY_DESTINATION"] = destination.name
    }

    // Import screen sub-tab (0 = Dokumentu apstrāde, 1 = Lejupielādēt Excel importu, 2 = Tabulu Preseti)
    var selectedImportTab = mutableIntStateOf(
        savedStateHandle.get<Int>("KEY_IMPORT_TAB") ?: 0
    )
        private set

    fun updateImportTab(tab: Int) {
        selectedImportTab.intValue = tab
        savedStateHandle["KEY_IMPORT_TAB"] = tab
    }

    // Active selected process step
    var selectedProcess = mutableStateOf(
        savedStateHandle.get<String>("KEY_SELECTED_PROCESS") ?: "1. Audzēšana"
    )
        private set

    fun updateSelectedProcess(process: String) {
        selectedProcess.value = process
        savedStateHandle["KEY_SELECTED_PROCESS"] = process
    }

    // OCR notes text
    var ocrNotesText = mutableStateOf(
        savedStateHandle.get<String>("KEY_OCR_NOTES") ?: "Sistēma gatava darbam. Gaida dokumentu augšupielādi..."
    )
        private set

    fun updateOcrNotes(notes: String) {
        ocrNotesText.value = notes
        savedStateHandle["KEY_OCR_NOTES"] = notes
    }

    // Raw scanned rows list
    val rawRowsList = mutableStateListOf<RawScannedRow>().apply {
        val jsonStr = savedStateHandle.get<String>("KEY_RAW_ROWS_JSON")
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    add(rawRowFromJson(obj))
                }
            } catch (_: Exception) {}
        }
    }

    fun saveRawRows() {
        try {
            val jsonArray = JSONArray()
            rawRowsList.forEach { row ->
                jsonArray.put(row.toJson())
            }
            savedStateHandle["KEY_RAW_ROWS_JSON"] = jsonArray.toString()
        } catch (_: Exception) {}
    }

    fun addRawRow(row: RawScannedRow) {
        rawRowsList.add(row)
        saveRawRows()
    }

    fun removeRawRow(index: Int) {
        if (index in rawRowsList.indices) {
            rawRowsList.removeAt(index)
            saveRawRows()
        }
    }

    fun updateRawCell(row: RawScannedRow, role: String, value: String) {
        row.setCell(role, value)
        saveRawRows()
    }

    // Shared production rows list for salaries & export
    val rowsList = mutableStateListOf<ProductionRow>().apply {
        val jsonStr = savedStateHandle.get<String>("KEY_PROD_ROWS_JSON")
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    add(productionRowFromJson(obj))
                }
            } catch (_: Exception) {}
        }
    }

    fun saveProdRows() {
        try {
            val jsonArray = JSONArray()
            rowsList.forEach { row ->
                jsonArray.put(row.toJson())
            }
            savedStateHandle["KEY_PROD_ROWS_JSON"] = jsonArray.toString()
        } catch (_: Exception) {}
    }

    private fun RawScannedRow.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        val cellsObj = JSONObject()
        cells.forEach { (k, v) -> cellsObj.put(k, v) }
        obj.put("cells", cellsObj)
        return obj
    }

    private fun rawRowFromJson(json: JSONObject): RawScannedRow {
        val id = json.optString("id", UUID.randomUUID().toString())
        val cellsObj = json.optJSONObject("cells")
        val cells = mutableMapOf<String, String>()
        if (cellsObj != null) {
            cellsObj.keys().forEach { k ->
                cells[k] = cellsObj.optString(k, "")
            }
        }
        return RawScannedRow(id = id, cells = cells)
    }

    private fun ProductionRow.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("taskName", taskName)
        obj.put("status", status)
        obj.put("date", date)
        obj.put("m3", m3)
        obj.put("masters", masters)
        obj.put("relatedTo", relatedTo)
        obj.put("type", type)
        return obj
    }

    private fun productionRowFromJson(json: JSONObject): ProductionRow {
        return ProductionRow(
            id = json.optString("id", UUID.randomUUID().toString()),
            taskName = json.optString("taskName", ""),
            status = json.optString("status", "Completed"),
            date = json.optString("date", ""),
            m3 = json.optString("m3", ""),
            masters = json.optString("masters", ""),
            relatedTo = json.optString("relatedTo", ""),
            type = json.optString("type", "Ražošana")
        )
    }
}

package com.example.myapplication.repository

import android.content.Context
import android.util.Log
import com.example.myapplication.models.TablePreset
import org.json.JSONObject
import java.io.File

object PresetRepository {
    private const val TAG = "PresetRepository"
    private const val FILE_NAME = "presets.json"

    private val presetsMap = mutableMapOf<String, TablePreset>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized && presetsMap.isNotEmpty()) return

        try {
            val userFile = File(context.filesDir, FILE_NAME)
            val jsonContent: String = if (userFile.exists()) {
                userFile.readText()
            } else {
                val assetContent = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
                // Copy asset defaults to internal storage so user can customize
                userFile.writeText(assetContent)
                assetContent
            }

            parsePresetsJson(jsonContent)
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing presets", e)
            loadDefaultHardcodedPresets()
        }
    }

    private fun parsePresetsJson(jsonContent: String) {
        presetsMap.clear()
        val rootObj = JSONObject(jsonContent)
        val keys = rootObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val presetObj = rootObj.getJSONObject(key)
            val preset = TablePreset.fromJsonObject(key, presetObj)
            presetsMap[key] = preset
        }
    }

    fun getAllPresets(context: Context): List<TablePreset> {
        init(context)
        return presetsMap.values.toList()
    }

    fun getPreset(context: Context, key: String): TablePreset? {
        init(context)
        val normalizedKey = getNormalizedKey(key)
        return presetsMap[normalizedKey] ?: presetsMap.values.firstOrNull()
    }

    fun getPresetByTaskName(context: Context, taskName: String): TablePreset? {
        init(context)
        return presetsMap.values.firstOrNull { 
            it.taskName.equals(taskName, ignoreCase = true) || taskName.contains(it.key, ignoreCase = true) 
        } ?: getPreset(context, taskName)
    }

    fun savePreset(context: Context, preset: TablePreset) {
        init(context)
        presetsMap[preset.key] = preset
        persistPresets(context)
    }

    fun deletePreset(context: Context, key: String) {
        init(context)
        presetsMap.remove(key)
        persistPresets(context)
    }

    private fun persistPresets(context: Context) {
        try {
            val rootObj = JSONObject()
            for ((key, preset) in presetsMap) {
                rootObj.put(key, preset.toJsonObject())
            }
            val userFile = File(context.filesDir, FILE_NAME)
            userFile.writeText(rootObj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting presets", e)
        }
    }

    private fun getNormalizedKey(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("audzē") || lower.contains("diefen") -> "diefenbacher"
            lower.contains("vakuum") || lower.contains("press") -> "vacuumpress"
            lower.contains("cnc") -> "cnc"
            else -> lower
        }
    }

    private fun loadDefaultHardcodedPresets() {
        presetsMap["diefenbacher"] = TablePreset(key = "diefenbacher", taskName = "1. Audzētājs")
        presetsMap["vacuumpress"] = TablePreset(key = "vacuumpress", taskName = "3. Vakuumprese")
        presetsMap["cnc"] = TablePreset(key = "cnc", taskName = "5. CNC")
    }
}

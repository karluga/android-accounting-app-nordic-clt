package com.example.myapplication.models

import org.json.JSONArray
import org.json.JSONObject

data class PresetColumn(
    val index: Int,
    val sourceHeader: String,
    val role: String,
    val outputMapping: String? = null,
    val dataType: String = "string",
    val nullable: Boolean = true,
    val normalizationRule: String? = null,
    val keywords: List<String> = emptyList(),
    val enumValues: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("index", index)
        json.put("source_header", sourceHeader)
        json.put("role", role)
        json.put("output_mapping", outputMapping ?: JSONObject.NULL)
        json.put("data_type", dataType)
        json.put("nullable", nullable)
        json.put("normalization_rule", normalizationRule ?: JSONObject.NULL)

        val kwArr = JSONArray()
        keywords.forEach { kwArr.put(it) }
        json.put("keywords", kwArr)

        val enumArr = JSONArray()
        enumValues.forEach { enumArr.put(it) }
        json.put("enum_values", enumArr)

        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): PresetColumn {
            val kwList = mutableListOf<String>()
            val kwArray = json.optJSONArray("keywords")
            if (kwArray != null) {
                for (i in 0 until kwArray.length()) {
                    kwList.add(kwArray.getString(i))
                }
            }

            val enumList = mutableListOf<String>()
            val enumArray = json.optJSONArray("enum_values")
            if (enumArray != null) {
                for (i in 0 until enumArray.length()) {
                    enumList.add(enumArray.getString(i))
                }
            }

            val outputMappingRaw = if (json.isNull("output_mapping")) null else json.optString("output_mapping")

            return PresetColumn(
                index = json.optInt("index", 0),
                sourceHeader = json.optString("source_header", ""),
                role = json.optString("role", ""),
                outputMapping = if (outputMappingRaw.isNullFrancoOrBlank()) null else outputMappingRaw,
                dataType = json.optString("data_type", "string"),
                nullable = json.optBoolean("nullable", true),
                normalizationRule = if (json.isNull("normalization_rule")) null else json.optString("normalization_rule"),
                keywords = kwList,
                enumValues = enumList
            )
        }

        private fun String?.isNullFrancoOrBlank(): Boolean {
            return this == null || this.isBlank() || this == "null"
        }
    }
}

data class TablePreset(
    val key: String,
    val taskName: String,
    val dateFormat: String = "%d/%m/%Y",
    val columns: List<PresetColumn> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("key", key)
        json.put("task_name", taskName)
        json.put("date_format", dateFormat)

        val colArr = JSONArray()
        columns.forEach { colArr.put(it.toJsonObject()) }
        json.put("columns", colArr)

        return json
    }

    companion object {
        fun fromJsonObject(key: String, json: JSONObject): TablePreset {
            val presetKey = json.optString("key", key)
            val taskName = json.optString("task_name", presetKey)
            val dateFormat = json.optString("date_format", "%d/%m/%Y")

            val columnsList = mutableListOf<PresetColumn>()
            val colArray = json.optJSONArray("columns")
            if (colArray != null) {
                for (i in 0 until colArray.length()) {
                    val colObj = colArray.getJSONObject(i)
                    columnsList.add(PresetColumn.fromJsonObject(colObj))
                }
            }

            return TablePreset(
                key = presetKey,
                taskName = taskName,
                dateFormat = dateFormat,
                columns = columnsList
            )
        }
    }
}

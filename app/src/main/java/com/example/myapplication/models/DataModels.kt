package com.example.myapplication.models

import java.util.UUID

// Data class representing raw scanned table rows before import generation
data class RawScannedRow(
    val id: String = UUID.randomUUID().toString(),
    val cells: MutableMap<String, String> = mutableMapOf()
) {
    fun getCell(key: String): String = cells[key] ?: ""
    fun setCell(key: String, value: String) { cells[key] = value }
}

// Data class representing an import row matching the template
data class ProductionRow(
    val id: String = UUID.randomUUID().toString(),
    var taskName: String = "",
    var status: String = "Completed",
    var date: String = "",
    var m3: String = "",
    var masters: String = "",
    var relatedTo: String = "",
    var type: String = "Ražošana"
)

data class EmployeeSalaryData(
    val id: String,
    val name: String,
    val role: String,
    val hours: Double,
    val hourlyRate: Double,
    val bonus: Double = 0.0,
    val totalM3Handled: Double,
    val totalMastersHandled: Int,
    val completedTasksCount: Int,
    val photoUri: String? = null // Can be "res:karlis", "res:andrejs" or standard content URI
) {
    val baseSalary: Double = hours * hourlyRate
    val totalEarnings: Double = baseSalary + bonus
}

// Data class representing 7-column Excel export summary row
data class ExcelExportRow(
    val taskName: String,
    val status: String = "Completed",
    val date: String,
    val m3: String,
    val mastersCount: String,
    val relatedTo: String,
    val type: String = "Ražošana"
)


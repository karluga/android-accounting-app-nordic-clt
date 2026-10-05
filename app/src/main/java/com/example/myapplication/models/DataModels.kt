package com.example.myapplication.models

import com.example.myapplication.R
import java.util.UUID

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

// Data class for fields that require handwriting/OCR validation
data class PendingReviewField(
    val fieldKey: String,
    val label: String,
    val currentValue: String,
    val imagePrompt: String,
    val progressText: String,
    val targetRowIndex: Int
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

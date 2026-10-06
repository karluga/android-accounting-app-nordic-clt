package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import com.example.myapplication.utils.ImportProcessor

@Composable
fun ExcelExportTabContent(
    rawRowsList: List<RawScannedRow>,
    selectedProcess: String,
    activePreset: TablePreset?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (fileName, exportRows) = remember(rawRowsList.size, selectedProcess) {
        ImportProcessor.generateExcelImportDataFromRaw(rawRowsList, activePreset, selectedProcess)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LEJUPIELĀDĒT EXCEL IMPORTU",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Lejupielādēt Excel importu",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ģenerēts kopsavilkums no pārbaudītajām rindām ar nosaukumu <date><production_step>.xlsx",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sagatavotā faila nosaukums:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fileName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "7 kolonnu izvades kopsavilkums (${exportRows.size} rindas):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // 7 Export Column Headers
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp)
                            ) {
                                TableHeaderCell("Task name", 130, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                                TableHeaderCell("Status", 100, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                                TableHeaderCell("Datums", 110, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                TableHeaderCell("Saražoti m3", 110, Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764))
                                TableHeaderCell("Kopā masteri", 110, Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764))
                                TableHeaderCell("Related to", 140, Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D))
                                TableHeaderCell("Tips", 100, Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0))
                            }

                            if (exportRows.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Vēl nav aprēķinātu rindu. Vispirms spiediet 'Ģenerēt importu' 1. cilnē.", fontSize = 13.sp)
                                }
                            } else {
                                exportRows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExportTableCell(row.taskName, 130)
                                        ExportTableCell(row.status, 100)
                                        ExportTableCell(row.date, 110)
                                        ExportTableCell(row.m3, 110)
                                        ExportTableCell(row.mastersCount, 110)
                                        ExportTableCell(row.relatedTo, 140)
                                        ExportTableCell(row.type, 100)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val savedPath = ImportProcessor.saveExcelImportFile(fileName, exportRows)
                            if (savedPath != null) {
                                Toast.makeText(context, "Fails veiksmīgi saglabāts Downloads:\n$savedPath", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Kļūda saglabājot failu Downloads mapē.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7E34), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Lejupielādēt Excel importu ($fileName)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

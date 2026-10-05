package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.R
import com.example.myapplication.models.EmployeeSalaryData
import com.example.myapplication.models.ProductionRow
import java.util.Locale

@Composable
fun SalariesScreen(
    rowsList: List<ProductionRow>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalM3 = rowsList.sumOf { it.m3.toDoubleOrNull() ?: 0.0 }
    val totalMasters = rowsList.sumOf { it.masters.toIntOrNull() ?: 0 }

    // Map of employeeId to custom photo URI string (simulating database storage)
    // Initialized with pre-defined photo states for the first 2 employees
    var employeePhotos by rememberSaveable {
        mutableStateOf(
            mapOf(
                "emp_1" to "res:karlis",
                "emp_2" to "res:andrejs",
                "emp_3" to null
            )
        )
    }

    // This data structure represents what will eventually be loaded from separate JSON files
    val baseEmployees = remember {
        listOf(
            EmployeeSalaryData(
                id = "emp_1",
                name = "Kārlis Ivars Braķis",
                role = "Audzēšana operators",
                hours = 160.0,
                hourlyRate = 12.50,
                totalM3Handled = 42.15,
                totalMastersHandled = 180,
                completedTasksCount = 24
            ),
            EmployeeSalaryData(
                id = "emp_2",
                name = "Andrejs Ņesterovičs",
                role = "Vakuumprese operators",
                hours = 152.5,
                hourlyRate = 13.00,
                totalM3Handled = 35.80,
                totalMastersHandled = 245,
                completedTasksCount = 38
            ),
            EmployeeSalaryData(
                id = "emp_3",
                name = "Liene Kalniņa",
                role = "CNC operators",
                hours = 168.0,
                hourlyRate = 15.00,
                totalM3Handled = 58.90,
                totalMastersHandled = 190,
                completedTasksCount = 41
            )
        )
    }

    // Merge base data with live metrics and custom photo state
    val employees = remember(baseEmployees, employeePhotos, totalM3, totalMasters) {
        baseEmployees.map { emp ->
            val liveBonus = when(emp.id) {
                "emp_1" -> totalM3 * 5.0
                "emp_2" -> totalMasters * 2.0
                else -> totalMasters * 3.0
            }
            emp.copy(
                bonus = liveBonus,
                totalM3Handled = emp.totalM3Handled + if (emp.id == "emp_1") totalM3 else 0.0,
                totalMastersHandled = emp.totalMastersHandled + if (emp.id != "emp_1") totalMasters else 0,
                completedTasksCount = emp.completedTasksCount + if (totalM3 > 0 || totalMasters > 0) 1 else 0,
                photoUri = employeePhotos[emp.id]
            )
        }
    }

    var selectedEmployeeForStats by remember { mutableStateOf<EmployeeSalaryData?>(null) }
    var targetPickerEmployeeId by remember { mutableStateOf<String?>(null) }

    // Real system photo picker contract
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            targetPickerEmployeeId?.let { id ->
                employeePhotos = employeePhotos.toMutableMap().apply { this[id] = it.toString() }
                Toast.makeText(context, "Darbinieka attēls veiksmīgi atjaunināts!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "STRĀDNIEKU ALGURĒĶINS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aprēķinātās algas",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uzklikšķini uz darbinieka kartiņas, lai apskatītu viņa kopējo paveikto darbu un vēsturiskos kopsavilkuma rādītājus.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Aktīvie dati no jaunajām lapām:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Kopā reģistrēti: ${java.lang.String.format(Locale.US, "%.3f", totalM3)} m³", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Kopā reģistrēti masteri: $totalMasters gab.", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "LIVE APRĒĶINS",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        items(employees) { emp ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedEmployeeForStats = emp },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Employee Image Badge with Real Photo Picker Trigger
                        Surface(
                            modifier = Modifier
                                .size(60.dp)
                                .clickable {
                                    Toast.makeText(context, "Izvēlēties jaunu attēlu darbiniekam: ${emp.name}", Toast.LENGTH_SHORT).show()
                                    targetPickerEmployeeId = emp.id
                                    photoPickerLauncher.launch("image/*")
                                },
                            shape = RoundedCornerShape(30.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val imagePainter = when {
                                    emp.photoUri == "res:karlis" -> painterResource(R.drawable.karlis)
                                    emp.photoUri == "res:andrejs" -> painterResource(R.drawable.andrejs)
                                    emp.photoUri != null -> {
                                        // Placeholder for custom uploaded URI (would normally use Coil)
                                        painterResource(R.drawable.default_employee)
                                    }
                                    else -> painterResource(R.drawable.default_employee)
                                }

                                Image(
                                    painter = imagePainter,
                                    contentDescription = "Employee Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = emp.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = emp.role, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "€${java.lang.String.format(Locale.US, "%.2f", emp.totalEarnings)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Skatīt statistiku ↗",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nostrādātās stundas:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${emp.hours} st. (likme €${emp.hourlyRate}/h)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ražošanas apjoma prēmija:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+€${java.lang.String.format(Locale.US, "%.2f", emp.bonus)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF28A745))
                    }
                }
            }
        }
    }

    // STATS MODAL FOR CLICKED EMPLOYEE
    selectedEmployeeForStats?.let { emp ->
        Dialog(onDismissRequest = { selectedEmployeeForStats = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DARBINIEKA KOPSAVILKUMS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = emp.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = emp.role,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    Text(
                        text = "Kopējā paveiktā darba statistika (In Total):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Stats lines with placeholder + live data combinations
                    StatsRowItem("Kopā apstrādātie materiāli (m³):", java.lang.String.format(Locale.US, "%.3f m³", emp.totalM3Handled))
                    StatsRowItem("Kopā saražotie masteri (gab):", "${emp.totalMastersHandled} gab.")
                    StatsRowItem("Pabeigtās darba lapas / uzdevumi:", "${emp.completedTasksCount} gab.")
                    StatsRowItem("Kopējās reģistrētās stundas:", "${emp.hours} stundas")
                    StatsRowItem("Vidējā prēmiju efektivitāte:", "€${java.lang.String.format(Locale.US, "%.2f", emp.bonus / emp.hours)} / st.")

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { selectedEmployeeForStats = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aizvērt")
                    }
                }
            }
        }
    }
}

@Composable
fun StatsRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutomaticPaymentsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Payment credentials input states
    var userName by rememberSaveable { mutableStateOf("") }
    var userIban by rememberSaveable { mutableStateOf("") }

    // Payment schedule state
    var selectedSchedule by rememberSaveable { mutableStateOf("Reizi mēnesī (Monthly)") }
    var scheduleDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "AUTOMĀTISKIE MAKSĀJUMI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEE7100), // Swedbank Orange tint
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Algu automātiskā izmaksa",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Konfigurē regulāros maksājumu uzdevumus strādnieku stundu un apjoma algu segšanai.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Swedbank Integration Alert Information Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), // Very light warm orange
                border = BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BANKAS SADERĪBA: Swedbank Gateway API",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Šis modulis ir tiešā veidā pielāgots tikai Swedbank biznesa klientu integrācijai. Katrs izveidotais maksājumu rīkojums vai automātiskais pārskaitījums drošības apsvērumu dēļ ir manuāli jāapstiprina bankas lietotnē ar Smart-ID parakstu.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Credentials section input fields
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Uzņēmuma maksātāja rekvizīti",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Pilns vārds / Uzņēmuma nosaukums") },
                        placeholder = { Text("SIA Nordic CLT") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userIban,
                        onValueChange = { userIban = it },
                        label = { Text("Konta numurs (IBAN)") },
                        placeholder = { Text("LV21HABA0123456789012") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Payment schedule input inputs section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Maksājumu grafika iestatījumi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Norādiet, cik bieži sistēmai ir automātiski jāģenerē algu izmaksu rīkojumi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedSchedule,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Maksājumu periodiskums") },
                            trailingIcon = {
                                Text(
                                    text = "▼",
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { scheduleDropdownExpanded = !scheduleDropdownExpanded },
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scheduleDropdownExpanded = !scheduleDropdownExpanded }
                        )
                        DropdownMenu(
                            expanded = scheduleDropdownExpanded,
                            onDismissRequest = { scheduleDropdownExpanded = false }
                        ) {
                            listOf(
                                "Reizi mēnesī (Monthly)",
                                "Reizi divās nedēļās (Bi-weekly)",
                                "Katru nedēļu (Weekly)"
                            ).forEach { schedule ->
                                DropdownMenuItem(
                                    text = { Text(schedule) },
                                    onClick = {
                                        selectedSchedule = schedule
                                        scheduleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // TRIGGER ACTION BUTTON
        item {
            Button(
                onClick = {
                    if (userName.isBlank() || userIban.isBlank()) {
                        Toast.makeText(context, "Lūdzu, aizpildiet visus rekvizītus (Vārdu un IBAN kontu) pirms iestatīšanas!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Automātiskais algu maksājumu rīkojums ieplānots Swedbank sistēmā ($selectedSchedule). Gaidiet Smart-ID pieprasījumu telefonā autentifikācijai.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE7100), contentColor = Color.White), // Swedbank brand color
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Aktivizēt automātisko maksājumu rīkojumu", fontWeight = FontWeight.Bold)
            }
        }
    }
}

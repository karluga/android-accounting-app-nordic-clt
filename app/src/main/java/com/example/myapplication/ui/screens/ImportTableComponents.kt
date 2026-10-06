package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun getGroupColors(group: Int): Triple<Color, Color, Color> {
    return when (group) {
        1 -> Triple(Color(0xFFFFEEF0), Color(0xFFF87171), Color(0xFF7F1D1D)) // Group 1: Import (Pink)
        2 -> Triple(Color(0xFFEFE6FF), Color(0xFFB39DDB), Color(0xFF3B0764)) // Group 2: Calculations (Lilac)
        else -> Triple(Color(0xFF1E1E24), Color(0xFF334155), Color(0xFFE2E8F0)) // Group 3: Secondary (Dark)
    }
}

@Composable
fun LegendBadge(label: String, bgColor: Color, borderColor: Color, textColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TableHeaderCell(
    text: String,
    widthDp: Int,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .background(backgroundColor)
            .border(0.5.dp, borderColor)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = textColor
        )
    }
}

@Composable
fun TableInputCell(
    value: String,
    widthDp: Int,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    bgColor: Color = Color.Transparent,
    textColor: Color = Color.Unspecified,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .background(bgColor)
            .padding(horizontal = 2.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f)) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 11.sp, color = if (textColor != Color.Unspecified) textColor else LocalContentColor.current),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExportTableCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

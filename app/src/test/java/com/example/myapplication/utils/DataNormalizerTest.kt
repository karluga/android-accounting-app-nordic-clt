package com.example.myapplication.utils

import com.example.myapplication.models.PresetColumn
import com.example.myapplication.models.RawScannedRow
import com.example.myapplication.models.TablePreset
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class DataNormalizerTest {

    @Test
    fun testNormalizeProjectCode() {
        // Dot removal
        assertEquals("R02", DataNormalizer.normalizeProjectCode("R.02"))
        assertEquals("R02", DataNormalizer.normalizeProjectCode("R.0.2"))

        // 'O' replaced with '0' after 'R'
        assertEquals("R02", DataNormalizer.normalizeProjectCode("RO2"))
        assertEquals("R0", DataNormalizer.normalizeProjectCode("RO"))
        assertEquals("R02-01", DataNormalizer.normalizeProjectCode("RO2-O1"))

        // 'Z' replaced with '2' after 'R'
        assertEquals("R02", DataNormalizer.normalizeProjectCode("ROZ"))
        assertEquals("R2", DataNormalizer.normalizeProjectCode("RZ"))
        assertEquals("R02-02", DataNormalizer.normalizeProjectCode("R02-OZ"))

        // Combined dots, 'O' and 'Z' replacements
        assertEquals("R02", DataNormalizer.normalizeProjectCode("R.O.Z"))
        assertEquals("R02", DataNormalizer.normalizeProjectCode("r.o.z"))
        assertEquals("R02-02", DataNormalizer.normalizeProjectCode("r.oz-0z"))
        assertEquals("R0202", DataNormalizer.normalizeProjectCode("r.oz.0z"))

        // Numeric prefix addition
        assertEquals("R02", DataNormalizer.normalizeProjectCode("2"))
        assertEquals("R02", DataNormalizer.normalizeProjectCode("02"))
        assertEquals("R01-02", DataNormalizer.normalizeProjectCode("01-02"))

        // Empty string
        assertEquals("", DataNormalizer.normalizeProjectCode(""))
    }

    @Test
    fun testIsForwardFillAllowed() {
        // Allowed columns
        val dateCol = PresetColumn(0, "Dat. 2026", "date", normalizationRule = "date_dd_mm")
        val opCol = PresetColumn(1, "Operat.", "operator", normalizationRule = "operator")
        val projCol = PresetColumn(2, "Proj.Nr.", "project", normalizationRule = "project_code")
        val thickCol = PresetColumn(4, "Biez.", "thickness", normalizationRule = "numeric")
        val panelCol = PresetColumn(10, "Panelis", "panel", normalizationRule = "panel_enum")

        Assert.assertTrue(DataNormalizer.isForwardFillAllowed(dateCol))
        Assert.assertTrue(DataNormalizer.isForwardFillAllowed(opCol))
        Assert.assertTrue(DataNormalizer.isForwardFillAllowed(projCol))
        Assert.assertTrue(DataNormalizer.isForwardFillAllowed(thickCol))
        Assert.assertTrue(DataNormalizer.isForwardFillAllowed(panelCol))

        // Excluded time columns
        val startTimeCol = PresetColumn(3, "Apstrādes sākšanas laiks", "start_time", normalizationRule = "time_hh_mm", forwardFill = false)
        val endTimeCol = PresetColumn(9, "Apstrādes beigšanas laiks", "end_time", normalizationRule = "time_hh_mm", forwardFill = false)
        val loadStartCol = PresetColumn(4, "Uzkraušana Sākta", "load_start", normalizationRule = "time_hh_mm", forwardFill = false)
        val cncStartCol = PresetColumn(5, "CNC sāk", "cnc_start", normalizationRule = "time_hh_mm", forwardFill = false)
        val cncEndCol = PresetColumn(6, "CNC beidz", "cnc_end", normalizationRule = "time_hh_mm", forwardFill = false)
        val unloadEndCol = PresetColumn(7, "Nokraušana pabeigta", "unload_end", normalizationRule = "time_hh_mm", forwardFill = false)
        val simDurationCol = PresetColumn(8, "Simulācijas laiks", "sim_duration", normalizationRule = "passthrough", forwardFill = false)

        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(startTimeCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(endTimeCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(loadStartCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(cncStartCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(cncEndCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(unloadEndCol))
        Assert.assertFalse(DataNormalizer.isForwardFillAllowed(simDurationCol))
    }

    @Test
    fun testSplitMergedColumns() {
        val preset = TablePreset(
            key = "test",
            taskName = "Test Task",
            columns = listOf(
                PresetColumn(0, "Dat. 2026", "date", normalizationRule = "date_dd_mm"),
                PresetColumn(1, "Operat.", "operator", normalizationRule = "operator"),
                PresetColumn(2, "Proj.Nr.", "project", normalizationRule = "project_code"),
                PresetColumn(3, "Apstrādes sākšanas laiks", "start_time", normalizationRule = "time_hh_mm"),
                PresetColumn(4, "Biez.", "thickness", normalizationRule = "numeric"),
                PresetColumn(5, "Plat.", "width", normalizationRule = "numeric")
            )
        )

        // Case 1: Minority (< 50% of rows have "R30 08:50"). 1 out of 3 rows = 33%.
        val row0 = RawScannedRow()
        row0.setCell("date", "03/07/2026")
        row0.setCell("operator", "VŠ")
        row0.setCell("project", "R30 08:50") // Merged!
        row0.setCell("start_time", "47")      // Shifted left
        row0.setCell("thickness", "150")      // Shifted left

        val row1 = RawScannedRow()
        row1.setCell("date", "03/07/2026")
        row1.setCell("operator", "VŠ")
        row1.setCell("project", "R31")
        row1.setCell("start_time", "09:00")
        row1.setCell("thickness", "47")
        row1.setCell("width", "150")

        val row2 = RawScannedRow()
        row2.setCell("date", "03/07/2026")
        row2.setCell("operator", "VŠ")
        row2.setCell("project", "R32")
        row2.setCell("start_time", "09:15")
        row2.setCell("thickness", "47")
        row2.setCell("width", "150")

        val rows = mutableListOf(row0, row1, row2)
        DataNormalizer.splitMergedColumns(rows, preset)

        // Verify row0 was split and shifted
        assertEquals("R30", row0.getCell("project"))
        assertEquals("08:50", row0.getCell("start_time"))
        assertEquals("47", row0.getCell("thickness"))
        assertEquals("150", row0.getCell("width"))
    }
}

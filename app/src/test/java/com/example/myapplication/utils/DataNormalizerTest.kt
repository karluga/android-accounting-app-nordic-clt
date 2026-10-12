package com.example.myapplication.utils

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
}

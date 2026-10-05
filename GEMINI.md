# GEMINI.md - High-Level Project Scope & Architecture

## Overview
This project is an **Android Timber Production & Accounting Management Application** ("Nordic CLT Accounting App"). It is designed for timber manufacturing and processing plants (e.g., CLT/Glulam production steps such as *Audzēšana/Finger-jointing*, *Vakuumprese/Vacuum Press*, and *CNC processing*).

The application enables plant managers and operators to:
1. **Digitize Worksheets via OCR:** Scan or upload PDF/image production sheets, automatically extracting structured tabular data using OCR (OCR.space API) combined with pre-defined table presets.
2. **Review & Edit Scanned Data:** Verify handwriting and low-confidence OCR fields through an interactive verification UI, and edit data in a dynamic spreadsheet component with color-coded column focus.
3. **Manage Table Presets & Rules:** Define custom table structures, column headers, data types (date, float, int, enum), validation rules, and output mappings for each production stage.
4. **Export Standardized Excel Imports:** Generate daily Excel import files named `<date><production_step>.xlsx` adhering to a strict 7-column output format.
5. **Calculate Salaries & Wages:** Automatically calculate employee wages, base pay, bonuses, working hours, total m³ processed, and masters handled based on production logs.
6. **Manage Automatic Payments:** Review pending supplier/partner invoices and execute or schedule automated payments.

---

## Application Architecture

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt                # Main container with NavigationSuiteScaffold
├── dependancy/
│   ├── IORCCallBack.kt            # Interface for OCR API async results
│   └── OCRAsyncTask.kt            # AsyncTask / HTTP POST client for OCR.space API
├── models/
│   ├── DataModels.kt              # ProductionRow, PendingReviewField, EmployeeSalaryData
│   └── PresetModels.kt            # TablePreset, PresetColumn JSON serializable models
├── repository/
│   └── PresetRepository.kt        # Repository loading default presets from assets/presets.json
│                                  # and persisting custom presets in internal storage
├── ui/
│   ├── screens/
│   │   ├── ImportScreen.kt        # Combined screen with sub-tabs: "Dokumentu apstrāde", "Lejupielādēt Excel importu", "Tabulu Preseti"
│   │   ├── PresetsTab.kt          # Preset Editor UI (Manage presets, task names, columns, rules)
│   │   ├── DocumentPreviewHelper.kt # Native PDF/Image rendering, page navigation & blue header rotation detection
│   │   ├── SalariesScreen.kt      # Employee salary calculation & metrics summary
│   │   └── AutomaticPaymentsScreen.kt # Payment scheduling & automated processing
│   └── theme/                     # Compose Material 3 styling & typography
└── assets/
    └── presets.json               # Default JSON configurations for production table presets
```

---

## Core Feature Modules & Requirements

### 1. Import Screen Tabs (`ImportScreen.kt`)
Divided into three dedicated sub-tabs:
1. **`Dokumentu apstrāde`** (Document Processing): Upload PDFs/images, preview & rotate pages, trigger OCR, verify handwriting fields, and edit parsed rows in the spreadsheet table.
2. **`Lejupielādēt Excel importu`** (Download Excel Import): Preview and download the generated daily import file formatted according to strict 7-column output rules.
3. **`Tabulu Preseti`** (Table Presets): Configure table structures, headers, data types, and mapping rules.

---

### 2. Spreadsheet Color Focus Hierarchy

To shift user focus to critical fields, table columns in `Dokumentu apstrāde` use distinct color coding:

| Group | Category | Background Color | Accent / Border | Description |
|---|---|---|---|---|
| **Group 1** | **Import** | `#FFEEF0` (Sārts / Light Pink) | `#F87171` | Essential identity fields (`Datums`, `Operat.`, `Proj.Nr.`) |
| **Group 2** | **Calculations** | `#EFE6FF` (Ceriņu / Light Lilac) | `#B39DDB` | Fields used for m³ and master calculations (`Biez.`, `Plat.`, `Izejošie metri`, `gb.`, `Dīkstāve un komentāri`, `Mastera nr.`) |
| **Group 3** | **Secondary** | `#1E1E24` (Melnbalts / Dark Slate) | `#334155` | Secondary operational fields (time stamps, panel types, comments) |

#### Process Column Color Assignments:
- **Diefenbacher (1. Audzētājs):**
  - Group 1 (Import): `Dat. 2026`, `Operat.`, `Proj.Nr.`
  - Group 2 (Calculations): `Biez.`, `Plat.`, `Izejošie metri`, `gb.`, `Dīkstāve un komentāri`
- **Vacuumpress (3. Vakuumprese):**
  - Group 1 (Import): `Dat. 2026`, `Operat.`, `Proj. Nr.`
  - Group 2 (Calculations): `Mastera nr.`, `Slāņu skaits/biezumi`, `Garenvirziena dēļi`, `Šķērsvirziena dēļi`
- **CNC (5. CNC):**
  - Group 1 (Import): `Dat. 2026`, `Operat.`, `Projekta Nr.`
  - Group 2 (Calculations): `Mastera. nr.`, `CNC sāk`, `CNC beidz`, `Divpusēja apstrāde`

---

### 3. Output Excel Format & Business Rules

#### File Naming Convention
Generated files are named:
$$\text{<date>}\text{<production\_step>}\text{.xlsx}$$
- Example: `03-07-2026_1_Audzetajs.xlsx`
- **Multi-page rule:** If multiple pages or rows exist with different dates, the file name takes the **date from the first row**.

#### 7-Column Output Table Structure:

| # | Column Name | Type | Allowed / Expected Values | Notes |
|---|---|---|---|---|
| 1 | `Task name` | text | `1. Audzētājs` / `3. Vakuumprese` / `5. CNC` | Step display name |
| 2 | `Status` | text | Always `Completed` | Completion state |
| 3 | `Datums` | date | `DD/MM/YYYY` | Date string |
| 4 | `Saražoti m3` | number | Calculated cubic meters | $\frac{\text{Metri} \times \text{Plat.} \times \text{Biez.}}{1\,000\,000}$ |
| 5 | `Kopā masteri` | int/text | Integer count or `"Brusas"` | Count of finished master panels |
| 6 | `Related to` | text | Project code (e.g. `PRJ-2026-A`) | Option for `_BRUSAS` suffix |
| 7 | `Tips` | text | Always `Ražošana` | Entry type |

#### Special Diefenbacher Calculation Rules:
- The Diefenbacher column `"Dīkstāve un komentāri"` contains master panel numbers.
- **Finished Master Rule:** Only entries marked with **`(OK)`** (e.g. `XXXX(OK)`) are counted as completed master panels in `Kopā masteri`.
- **m³ Calculation:** `Saražoti m3` is calculated from ALL manufactured entries for that day/project, while `Kopā masteri` counts only those marked `(OK)`. Every entry for the same day, step, and project is combined into a single summary output row.

---

## Technical Specifications
- **Framework:** Jetpack Compose with Material 3 Adaptive Navigation Suite.
- **Language:** Kotlin 2.x
- **Min SDK:** 24 (Android 7.0) | **Target SDK:** 37
- **Networking:** HTTPS Connection to OCR.space API (`https://api.ocr.space/parse/image`).
- **Data Persistence:** Android Assets (`assets/presets.json`) + Internal File I/O (`filesDir`).

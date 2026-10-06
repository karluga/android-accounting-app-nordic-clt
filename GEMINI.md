# GEMINI.md - High-Level Project Scope & Architecture

## Overview
This project is an **Android Timber Production & Accounting Management Application** ("Nordic CLT Accounting App"). It is designed for timber manufacturing and processing plants (e.g., CLT/Glulam production steps such as *Audzēšana/Finger-jointing*, *Vakuumprese/Vacuum Press*, and *CNC processing*).

The application enables plant managers and operators to:
1. **Digitize Worksheets via OCR:** Scan or upload PDF/image production sheets, automatically extracting raw tabular rows using OCR (OCR.space API) combined with pre-defined table presets.
2. **Manually Verify & Edit Raw Rows:** Verify and edit all individual scanned columns in an interactive spreadsheet table with color-coded column focus before generating the import file.
3. **Manage Table Presets & Rules:** Define custom table structures, column headers, data types (date, float, int, enum), validation rules, and output mappings for each production stage.
4. **Generate & Download Excel Imports:** Convert verified raw rows into a daily 7-column import file named `<date><production_step>.xlsx`.
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
│   ├── DataModels.kt              # RawScannedRow, ProductionRow, PendingReviewField, EmployeeSalaryData
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

## Core Feature Modules & Workflow

### 1. Import Workflow & Sub-Tabs (`ImportScreen.kt`)
The Import screen is structured into three dedicated sub-tabs:
1. **`Dokumentu apstrāde`** (Document Processing): Upload PDFs/images, preview & rotate pages, trigger OCR, and **manually verify/edit all raw scanned rows** in the editable spreadsheet table. Contains the **"Ģenerēt importu"** button at the bottom.
2. **`Lejupielādēt Excel importu`** (Download Excel Import): Displays the generated 7-column summary table and file name preview (`<date><production_step>.xlsx`), with a download action button.
3. **`Tabulu Preseti`** (Table Presets): Configure table structures, headers, data types, and mapping rules.

---

### 2. Raw Scanned Columns & Visual Color Focus

Operators edit the full set of raw scanned columns for each process step in Tab 1 before generating the import file.
When switching the process step dropdown, if the table is empty, the table headers and initial rows automatically update to match the active preset's column structure.

#### Column Color Coding Scheme:
- **Group 1 (Import - Pink `#FFEEF0`):** Critical identity fields (`Datums`, `Operat.`, `Proj.Nr.`).
- **Group 2 (Calculations - Lilac `#EFE6FF`):** Fields used for m³ and master calculations.
- **Group 3 (Secondary - Dark `#1E1E24`):** Secondary operational fields (time stamps, panel types, comments).

#### Complete Raw Column Lists per Step:

- **Diefenbacher (1. Audzētājs):**
  1. `Dat. 2026` (Group 1 - Pink)
  2. `Operat.` (Group 1 - Pink)
  3. `Proj.Nr.` (Group 1 - Pink)
  4. `Apstrādes sākšanas laiks` (Group 3 - Dark)
  5. `Biez.` (Group 2 - Lilac)
  6. `Plat.` (Group 2 - Lilac)
  7. `Ienākošie metri` (Group 3 - Dark)
  8. `Izejošie metri` (Group 2 - Lilac)
  9. `gb.` (Group 2 - Lilac)
  10. `Apstrādes beigšanas laiks` (Group 3 - Dark)
  11. `Panelis` (Group 3 - Dark)
  12. `Slānis` (Group 3 - Dark)
  13. `Dīkstāve un komentāri` (Group 2 - Lilac, contains master panel number e.g. `1001(OK)`)

- **Vakuumprese (3. Vakuumprese):**
  1. `Dat. 2026` (Group 1 - Pink)
  2. `Operat.` (Group 1 - Pink)
  3. `Mastera nr.` (Group 2 - Lilac)
  4. `Proj. Nr.` (Group 1 - Pink)
  5. `Apstrādes uzsākšanas laiks` (Group 3 - Dark)
  6. `Slāņu skaits/biezumi` (Group 2 - Lilac)
  7. `Garenvirziena dēļi - platums` (Group 2 - Lilac)
  8. `Garenvirziena dēļi - skaits` (Group 2 - Lilac)
  9. `Šķērsvirziena dēļi - platums` (Group 2 - Lilac)
  10. `Šķērsvirziena dēļi - skaits` (Group 2 - Lilac)
  11. `Apstrādes pabeigšanas laiks` (Group 3 - Dark)
  12. `Dīkstāve un komentāri` (Group 3 - Dark)

- **CNC (5. CNC):**
  1. `Dat. 2026` (Group 1 - Pink)
  2. `Operat.` (Group 1 - Pink)
  3. `Projekta Nr.` (Group 1 - Pink)
  4. `Mastera. nr.` (Group 2 - Lilac)
  5. `Uzkraušana Sākta` (Group 3 - Dark)
  6. `CNC sāk` (Group 2 - Lilac)
  7. `CNC beidz` (Group 2 - Lilac)
  8. `Nokraušana pabeigta` (Group 3 - Dark)
  9. `Simulācijas laiks` (Group 3 - Dark)
  10. `Divpusēja apstrāde (jā/nē)` (Group 2 - Lilac)
  11. `Dīkstāve un komentāri` (Group 3 - Dark)

---

### 3. Import Generation & Output Rules

#### Generation Trigger
Under the editable table in Tab 1, clicking **"Ģenerēt importu"** calculates the summary data from the verified raw cells and automatically navigates the user to **Tab 2 (Lejupielādēt Excel importu)** with the generated file highlighted.

#### File Naming Convention
Generated files are named:
$$\text{<date>}\text{<production\_step>}\text{.xlsx}$$
- Example: `03-07-2026_1_Audzetajs.xlsx`
- Taking the **date from the first raw row** if multiple rows/pages exist.

#### 7-Column Output Table Structure:

| # | Column Name | Type | Allowed / Expected Values | Notes |
|---|---|---|---|---|
| 1 | `Task name` | text | `1. Audzētājs` / `3. Vakuumprese` / `5. CNC` | Step display name |
| 2 | `Status` | text | Always `Completed` | Completion state |
| 3 | `Datums` | date | `DD/MM/YYYY` | Date string |
| 4 | `Saražoti m3` | number | Calculated cubic meters | Sum of $(\text{Metri} \times \text{Plat.} \times \text{Biez.}) / 1\,000\,000$ |
| 5 | `Kopā masteri` | int/text | Integer count or `"Brusas"` | Count of finished master panels |
| 6 | `Related to` | text | Project code (e.g. `Kolonnu apdare T3`) | Project identity |
| 7 | `Tips` | text | Always `Ražošana` | Entry type |

#### Diefenbacher Calculation Rules:
- The column `"Dīkstāve un komentāri"` contains master panel numbers.
- **Finished Master Rule:** Only entries marked with **`(OK)`** (e.g. `1001(OK)`) are counted in `Kopā masteri`.
- **m³ Calculation:** `Saražoti m3` is calculated from ALL manufactured entries for that day/project, while `Kopā masteri` counts only those marked `(OK)`.

---

## Technical Specifications
- **Framework:** Jetpack Compose with Material 3 Adaptive Navigation Suite.
- **Language:** Kotlin 2.x
- **Min SDK:** 24 (Android 7.0) | **Target SDK:** 37
- **Networking:** HTTPS Connection to OCR.space API (`https://api.ocr.space/parse/image`).
- **Data Persistence:** Android Assets (`assets/presets.json`) + Internal File I/O (`filesDir`).

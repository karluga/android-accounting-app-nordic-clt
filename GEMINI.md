# GEMINI.md - High-Level Project Scope & Architecture

## Overview
This project is an **Android Timber Production & Accounting Management Application** ("Nordic CLT Accounting App"). It is designed for timber manufacturing and processing plants (e.g., CLT/Glulam production steps such as *Audzēšana/Finger-jointing*, *Vakuumprese/Vacuum Press*, and *CNC processing*).

The application enables plant managers and operators to:
1. **Digitize Worksheets via OCR:** Scan or upload PDF/image production sheets, automatically extracting structured tabular data using OCR (OCR.space API) combined with pre-defined table presets.
2. **Review & Edit Scanned Data:** Verify handwriting and low-confidence OCR fields through an interactive verification UI, and edit data in a dynamic spreadsheet component.
3. **Manage Table Presets & Rules:** Define custom table structures, column headers, data types (date, float, int, enum), validation rules, and output mappings for each production stage.
4. **Calculate Salaries & Wages:** Automatically calculate employee wages, base pay, bonuses, working hours, total m³ processed, and masters handled based on production logs.
5. **Manage Automatic Payments:** Review pending supplier/partner invoices and execute or schedule automated payments.

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
│   │   ├── ImportScreen.kt        # Combined screen with tabs: "Dokumentu Imports" & "Tabulu Preseti"
│   │   ├── PresetsTab.kt          # Preset Editor UI (Manage presets, task names, columns, rules)
│   │   ├── SalariesScreen.kt      # Employee salary calculation & metrics summary
│   │   └── AutomaticPaymentsScreen.kt # Payment scheduling & automated processing
│   └── theme/                     # Compose Material 3 styling & typography
└── assets/
    └── presets.json               # Default JSON configurations for production table presets
```

---

## Core Feature Modules

### 1. Document Import & Table Reading (`ImportScreen.kt`)
- **Document Ingestion:** Select PDF files or images from device storage using system contracts.
- **OCR Engine Integration:** Sends document image data (Base64 or URL) to `OCR.space` API with parameters `isTable=true`, `OCREngine=2`, and `language=eng`/`lat`.
- **Dynamic Preset Mapping:** Always enforces predefined column headers from the active preset (e.g. `diefenbacher`, `vacuumpress`, `cnc`) on top of OCR output.
- **Handwriting Verification:** Prompts user for manual verification if fields fall below OCR confidence threshold (<80%) or in teaching mode.
- **Spreadsheet Editing:** Inline editable Jetpack Compose data grid for `ProductionRow` data.

### 2. Table Presets & Header Rules (`PresetsTab.kt` & `PresetRepository.kt`)
- **Asset Storage:** Default table structures, headers, and normalization rules are stored in `app/src/main/assets/presets.json`.
- **Dynamic Configuration:** Supports adding/modifying production steps, source column headers, roles (date, operator, project, m³ input, amount, comments), data types, enum options, and keywords.
- **Persistence:** Custom presets created by users are saved to internal application storage (`context.filesDir/presets.json`).

### 3. Salary & Productivity Metrics (`SalariesScreen.kt`)
- **Dynamic Calculation:** Aggregates production rows from `ImportScreen` to calculate `totalM3Handled`, `totalMastersHandled`, and `completedTasksCount` per employee.
- **Wage Summary:** Computes base pay, bonuses, total earnings, and average hourly productivity.

### 4. Automatic Payments (`AutomaticPaymentsScreen.kt`)
- **Payment Processing:** Displays scheduled payments, status tracking, payment execution, and history export.

---

## Technical Specifications
- **Framework:** Jetpack Compose with Material 3 Adaptive Navigation Suite.
- **Language:** Kotlin 2.x
- **Min SDK:** 24 (Android 7.0) | **Target SDK:** 37
- **Networking:** HTTPS Connection to OCR.space API (`https://api.ocr.space/parse/image`).
- **Data Persistence:** Android Assets (`assets/presets.json`) + Internal File I/O (`filesDir`).

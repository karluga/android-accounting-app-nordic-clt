# Feature Documentation: Table Reading & OCR Integration

## Overview
The Table Reading functionality allows users to capture or select scanned paper production sheets (PDF or images) and parse them into structured tabular rows inside the Android application.

No matter what OCR engine returns, **the table structure and header row are strictly determined by pre-defined Table Presets** (`presets.json`). This ensures uniform data formatting for downstream processes like Excel export and wage calculations.

---

## Data Architecture for Table Presets

### Storage Location
- **Default Presets:** `app/src/main/assets/presets.json`
- **User Custom Presets:** `context.filesDir + "/presets.json"`

### JSON Schema Structure (`PresetModels.kt`)
Each preset contains:
- `key`: Unique identifier (e.g. `diefenbacher`, `vacuumpress`, `cnc`).
- `task_name`: Display name (e.g. `1. Audzētājs`, `3. Vakuumprese`, `5. CNC`).
- `date_format`: Pattern string for dates (e.g. `%d/%m/%Y`).
- `columns`: List of column definitions:
  - `index`: 0-based column sequence index.
  - `source_header`: Primary expected header string on scanned document.
  - `role`: Semantic field role (`date`, `operator`, `project`, `start_time`, `end_time`, `thickness`, `width`, `incoming_m`, `outgoing_m`, `amount`, `panel`, `layer`, `comments`).
  - `output_mapping`: Target standard field mapping (`Datums`, `Related to`, `m3 input`, `Kopā masteri`, etc.).
  - `data_type`: Data type constraint (`date`, `string`, `float`, `int`, `enum`).
  - `nullable`: Boolean flag.
  - `normalization_rule`: Parsing rule (e.g., `date_dd_mm`, `numeric`, `project_code`).
  - `keywords`: Expected OCR trigger words.
  - `enum_values`: Whitelist of allowed string values for `enum` fields.

---

## OCR Space API Workflow (`OCRAsyncTask.kt`)

1. **Document Selection:**
   User chooses an image or PDF file via Android System File Picker (`ActivityResultContracts.GetMultipleContents()`).
2. **Base64 Encoding / Transfer:**
   Local Uri is converted to a Base64 string (`data:image/jpeg;base64,...`) or sent as a URL POST to OCR.space endpoint `https://api.ocr.space/parse/image`.
3. **API Parameters Sent:**
   - `apikey`: `BuildConfig.OCR_API_KEY` (loaded from `.env` environment variable).
   - `language`: `eng` / `lat`.
   - `isOverlayRequired`: `true` (enables word positioning).
   - `isTable`: `true` (enables OCR.space table parsing engine).
   - `OCREngine`: `2` (recommended for tables, numbers, and latin text).
4. **Result Parsing:**
   - Extracts parsed text lines from response JSON (`ParsedResults[0].ParsedText`).
   - Maps extracted row fields to the columns defined in the active `TablePreset`.
   - Populates `ProductionRow` objects into the Compose state `rowsList`.

---

## Dynamic Spreadsheet Rendering

- The spreadsheet view in `ImportScreen.kt` dynamically constructs table headers using `PresetColumn.source_header` or `output_mapping` from `PresetRepository.getActivePreset(presetKey)`.
- Input fields allow real-time inline editing of extracted values before export.
- Handwriting review prompts (`PendingReviewField`) pop up for fields with low confidence or when teaching mode is enabled.

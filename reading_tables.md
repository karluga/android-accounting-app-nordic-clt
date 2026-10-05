# Feature Documentation: Table Reading & OCR Integration

## Overview
The Table Reading functionality allows users to capture or select scanned paper production sheets (PDF or images), preview and rotate pages, and parse them into structured tabular rows inside the Android application.

No matter what OCR engine returns, **the table structure and header row are strictly determined by pre-defined Table Presets** (`presets.json`). This ensures uniform data formatting for downstream processes like Excel export and wage calculations.

---

## Document Ingestion & Image Orientation Pre-Processing

### Multi-Page Navigation & Rotation (`DocumentPreviewHelper.kt`)
1. **Multi-Page & Multi-File Support:**
   - Supports single/multiple image files (`image/*`) and multi-page PDF documents (`application/pdf`).
   - Uses Android's native `android.graphics.pdf.PdfRenderer` to unpack multi-page PDFs into distinct, high-resolution bitmap page instances.
   - Provides full page navigation ("Lapa X no Y", "Iepriekšējā lapa", "Nākamā lapa").
2. **Max Width Preview:**
   - Previews the rendered page bitmap scaled to the maximum width of the screen (`Modifier.fillMaxWidth()` with `ContentScale.FillWidth`).
3. **Automatic Orientation via Blue Header Detection:**
   - Evaluates HSV color space (`Hue ~165°..255°`, `Saturation >= 0.18`, `Value >= 0.20`) across pixel region boundaries (Top 25%, Bottom 25%, Left 25%, Right 25%).
   - Calculates necessary rotation angle (0°, 90°, 180°, 270°) to automatically align the blue header row to the top edge.
   - Automatically runs on page load and offers manual trigger button *"✨ Automātiski pielāgot (Meklēt zilo virsrakstu)"*.
4. **Manual Image Rotation Controls:**
   - Enables manual fine-tuning in 90° increments (-90°, +90°, reset to 0°).
   - Rotation matrix transformations are applied both to the Compose UI preview (`graphicsLayer(rotationZ = angle)`) and to the bitmap payload sent to OCR.space API.

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
- `columns`: List of column definitions (`index`, `source_header`, `role`, `output_mapping`, `data_type`, `nullable`, `keywords`, `enum_values`).

---

## OCR Space API Workflow (`OCRAsyncTask.kt`)

1. **Document Selection & Orientation:**
   User chooses files, navigates pages, and rotates the image to upright orientation automatically or manually.
2. **Rotated Base64 Encoding:**
   The rotated bitmap is compressed to JPEG and encoded to Base64 (`Base64.encodeToString(bytes, Base64.NO_WRAP)`).
3. **API Parameters Sent:**
   - `apikey`: `BuildConfig.OCR_API_KEY` (loaded from `.env` environment variable).
   - `language`: `eng` / `lat`.
   - `isOverlayRequired`: `true` (enables word positioning).
   - `isTable`: `true` (enables OCR.space table parsing engine).
   - `OCREngine`: `2` (recommended for tables, numbers, and latin text).
4. **Result Parsing:**
   - Maps extracted row fields to the columns defined in the active `TablePreset`.
   - Populates `ProductionRow` objects into the Compose state `rowsList`.

---

## Dynamic Spreadsheet Rendering

- The spreadsheet view in `ImportScreen.kt` dynamically constructs table headers using `PresetColumn.source_header` or `output_mapping` from `PresetRepository.getActivePreset(presetKey)`.
- Input fields allow real-time inline editing of extracted values before export.
- Handwriting review prompts (`PendingReviewField`) pop up for fields with low confidence or when teaching mode is enabled.

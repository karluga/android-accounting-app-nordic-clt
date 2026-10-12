# Nordic CLT — Data Normalization Reference & Expected Edge Cases

This document specifies how raw OCR text from scanned production paperwork is sanitized, normalized, and transformed into clean, standardized values before data export and archive storage.

Special rules:
Diefenbacher column "Dīkstāve un komentāri" actually contains the master panel number. To the resulting import file we can only use the finished ones numbered XXXX(OK).
For calculating these columns: "Saražoti m3"	"Kopā masteri"
m3 we count from all the manufactured panels but "Kopā masteri" we count with (OK) marking.
---

## 1. Header Row Handling
* **Behavior:** The top row (row 0) of every scanned table on paper is the printed header row.
* **Rule:** Row 0 is detected and discarded/ignored. Column names are strictly determined by the selected manufacturing step preset (`diefenbacher`, `vacuumpress`, `cnc`), ensuring consistent column roles regardless of handwriting or print variations in the header area.

---

## 2. Alternating & Blank Rows
Sometimes data can be like this:
filled row
empty row
filled
empty
...
make sure OCR doesnt keep empty rows
---

## 3. Date Normalizations (`Dat. 2026` / `Datums`)
* **Target Format:** Internal `DD/MM/YYYY` (Export format configurable per preset, e.g. `DD/MM/YYYY` or `DD.MM.YYYY.`).
* **Source Variations & Handling:**
  * **Date can never be 0:** e.g., 00/01/2026
  * **4 digits (DDMM):** e.g., `2808` → converts to `28/08/<current_year>` (e.g., `28/08/2026`).
  * **6 digits (DDMMYY):** e.g., `280826` → converts to `28/08/2026`.
  * **8 digits (DDMMYYYY):** e.g., `28082026` → converts to `28/08/2026`.
  * **Standard separators (DD.MM, DD/MM, DD-MM):** e.g., `28.08`, `28/08` → converts to `28/08/2026`.
  * **Date forward-filling:** Blank date cells in a page automatically inherit the active date from the last filled row on that same page. (with this same logic, when editing a date cell, all dates below change along with this cell)

---

## 4. Time Normalizations (`Apstrādes sākšanas / pabeigšanas laiks`)
* **Target Format:** `HH:MM` (24-hour time).
* **Source Variations & Handling:**
  * **Decimals from numeric OCR:** e.g., `1610.0` → `16:10`, `1145.0` → `11:45`, `1420.0` → `14:20`.
  * **4 digits (HHMM):** e.g., `0850` → `08:50`, `1530` → `15:30`, `1155` → `11:55`.
  * **3 digits (HMM):** e.g., `850` → `08:50`, `915` → `09:15`.
  * **Space separated (HH MM):** e.g., `08 50` → `08:50`.
  * **OCR digit confusion (e.g. `84:50` / `8450`):** OCR sometimes misreads `08` as `84`. Hours greater than 23 starting with `84` or `80..89` are corrected to `08:MM` (e.g., `84:50` → `08:50`).

---

## 5. Operator Whitelists & Initial Normalizations
Only allowed operators (converted to uppercase): 
Vakuumprese operatori
DSA; AGU

CNC operatori
ABA; RGO; RVE

Diefenbacher operatori
VŠ/VŠĻ; GPU;
**all of these are defined by the user in PresetsTab, but these ones are already added**
**just like the date, machine operators are also passed down from the last filled cell, and editing them does the same**
---

## 6. Project Code Normalization (`Proj. Nr.`)
* **Format:** Uppercase prefix `R` followed by numbers (e.g. `R02`).
* **OCR Fixes:** Misreads like `RO2`, `ROZ`, `R.02`, `2` → normalized to `R02`.
* **Repeating the same manufacturing project:** There will sometimes be "R18-1"; "R18-2"; "R18-3"
---

## 7. Panel & Layer Enums
* **Panelis:** Enum restricted to `V/V`, `V/N`, `N/N`.
* **Slānis:** Enum restricted to `VIZ`, `NEV`.
Normalizes common OCR misreads like `NEU` → `NEV`, `VIZ/NEU` → `VIZ/NEV`.
Anything starting with "V", ending with "Z" -> VIZ
Anything starting with "N", ending with "V", contains "E" -> NEV

---

## 8. Numeric & Master Count Fields
* **Numeric Fields (`biezums`, `platums`, `metri`):** Stripped of non-numeric characters except decimal points (`.`). `ISC` → `150`. // this

the company uses standard planks from PATA TIMBER. this also needs to be user-defined in presets but already defined in the app // this
# biezums x platums
# 47x150
# 45x170

## 9. Diefenbacher > Izejošie metri column
This will have an extra input field for the user in presets how large the panels can be.
If "izejošie metri" nuber divided by "gb." is bigger than 13, then we clear the field.

---

## 10. Semantic Column Alignment, Token Splitting & Diefenbacher Normalization

### A. Individual Cell Token Splitting (`split_merged_cell_token`)
When raw OCR outputs multiple merged fields into a single cell token, string pattern matching is used to split the single cell into distinct sequential tokens before column mapping:

1. **Operator + Project Code Splitting:**
   - **Target Pattern:** Operator acronym (`ABA`, `RGO`, `RVE`, `DSA`, `AGU`, `VŠ`, `VŠĻ`, `GPU`, `GR`) followed by space and a project code.
   - **Regex Pattern:** `^(ABA|RGO|RVE|DSA|AGU|VŠ|VŠĻ|GPU|GR)\s+([rR]?\-?\d+\w*)$` (case-insensitive).
   - **Examples:**
     - `"ABA R30"` $\rightarrow$ `["ABA", "R30"]`
     - `"RGO R18"` $\rightarrow$ `["RGO", "R18"]`
     - `"DSA R02"` $\rightarrow$ `["DSA", "R02"]`

2. **Master Panel ID + Project Code/Number Splitting:**
   - **Target Pattern:** 5–8 digit master panel ID followed by a space and a project code or project number.
   - **Regex Pattern:** `^(\d{5,8})\s+([rR]?\-?\d+\w*)$` (case-insensitive).
   - **Examples:**
     - `"2015002 R-31"` $\rightarrow$ `["2015002", "R-31"]`
     - `"2017000 31"` $\rightarrow$ `["2017000", "31"]`

3. **General Alphanumeric Merged Pair Splitting:**
   - **Target Pattern:** 1–8 character alphanumeric code space-separated from a project code (`R`/`r` prefix) or 5–8 digit ID.
   - **Regex Pattern:** `^([a-zA-Z0-9]{1,8})\s+([rR]\-?\d+\w*|\d{5,8})$`.
   - **Result:** Expanded into `[token_1, token_2]`.

---

### B. Diefenbacher Row Normalization & Semantic Field Alignment (`align_diefenbacher_tokens` & `fix_merged_row_fields`)

1. **Merged Project & Start Time Splitting:**
   - **Regex Pattern:** `^([rR]\.?[0-9]{1,4}(?:\s+[I|V|X]+)?(?:[a-zA-Z0-9_\-]*))\s+(\d{1,2}[:.\-]\d{2})$`
   - **Action:** Extracts `project` and `start_time` from a single string. Normalizes time hyphens/dots to colons (`:`).
   - **Examples:**
     - `"R30 08:50"` $\rightarrow$ `project = "R30"`, `start_time = "08:50"`
     - `"R18 III 08-50"` $\rightarrow$ `project = "R18 III"`, `start_time = "08:50"`

2. **Merged Panel Quality & Layer Enum Splitting:**
   - **Regex Pattern:** `\b(V/V|V/N|N/N)\s+([a-zA-Z]{1,4})\b` (case-insensitive).
   - **Action:** Splits quality enum and layer enum into distinct roles. Removes extraneous `"VIZ "` prefix if present in `layer`.
   - **Examples:**
     - `"V/V N"` $\rightarrow$ `panel = "V/V"`, `layer = "N"`
     - `"V/N V"` $\rightarrow$ `panel = "V/N"`, `layer = "V"`

3. **Master Panel ID Extraction & Normalization:**
   - **Identification Regex:** `(20\d{5}|\d{7})(?:[\s/(\[]+(\d{1,2})\)?)*`
   - **Suffix Normalization Rule:** If the 7-digit ID has a 2-digit trailing suffix (e.g., `/04`, `(04)`, ` 04`), the suffix is converted to `(OK)`.
   - **Field Relocation Rule:** If a Master Panel ID appears inside `comments`, `layer`, `end_time`, or `panel`, it is extracted, sanitized to `<master_id>(OK)`, placed into `comments`, and cleared from the source field.
   - **Examples:**
     - `"2016002/04)"` $\rightarrow$ `"2016002(OK)"`
     - `"2018000(04)"` $\rightarrow$ `"2018000(OK)"`
     - `"2015000 04"` $\rightarrow$ `"2015000(OK)"`

4. **Diefenbacher Numeric Field Column Shifting:**
   - **Trigger Condition:** Column 6 (`Ienākošie metri`) is intended to remain blank (`""`). If OCR places numbers in `incoming_m` and `outgoing_m` while `amount` (`gb.`) is empty:
     - Set `incoming_m = ""`
     - Shift `incoming_m` value $\rightarrow$ `outgoing_m`
     - Shift `outgoing_m` value $\rightarrow$ `amount` (`gb.`)
   - **Example:** `incoming_m="105"`, `outgoing_m="9"`, `amount=""` $\rightarrow$ `incoming_m=""`, `outgoing_m="105"`, `amount="9"`.

5. **OCR Notes Display:**
   - Both raw OCR API Markdown response (`BEFORE EDITS`) and sanitized table (`AFTER EDITS`) are logged side-by-side in Markdown format under "OCR piezīmes".

6. **Manual Column Data Swapping UI:**
   - Users can click any two column header titles (`<th>`) in the interactive table to swap all cell values between those two columns across all rows in the dataset.

---

## 11. Vakuumprese & CNC Positional Mapping & Row Reordering Rules

### A. Positional Token Alignment & Token Flattening (`align_vacuumpress_tokens`)
1. **Token Expansion:** Each cell in the OCR row is passed through `split_merged_cell_token()` to unpack any merged tokens (e.g. `"ABA R30"` $\rightarrow$ `["ABA", "R30"]`).
2. **Positional Mapping:** The flattened token list is mapped 1-to-1 to preset columns in left-to-right positional order.
3. **End Padding Rule:** If a row contains fewer tokens than the preset column count (e.g., 11 cells instead of 12 due to omitted empty OCR cells), empty strings (`""`) are padded **strictly at the very end** of the token array. Data is never shifted or inserted into middle columns.

### B. Row Reordering Policy
- **Vakuumprese & CNC Tables:** Row reordering is strictly disabled to prevent misalignment.
- **Diefenbacher Tables:** Row reordering remains enabled when needed.

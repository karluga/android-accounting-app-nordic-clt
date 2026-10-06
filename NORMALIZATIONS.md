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
* **Numeric Fields (`biezums`, `platums`, `metri`):** Stripped of non-numeric characters except decimal points (`.`). `ISC` → `150`.

the company uses standard planks from PATA TIMBER. this also needs to be user-defined in presets but already defined in the app
# biezums x platums
# 47x150
# 45x170

## 9. Diefenbacher > Izejošie metri column
This will have an extra input field for the user in presets how large the panels can be.
If "izejošie metri" nuber divided by "gb." is bigger than 13, then we clear the field.

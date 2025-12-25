# 📍 UN/LOCODE: Codes and Abbreviations Guide

## 📋 Contents and Layout

UN/LOCODE data is presented in 11 columns with the following structure:

> **Note:** This description applies to UN/LOCODE web pages only. For downloadable files (MS Access, CSV, or Text),-+-+-+-+-+
# 📍 UN/LOCODE: Codes and Abbreviations Guide

## 📋 Table of Contents

The UN approved Location codes are presented in 11 columns with specific content and structure.

> **Note:** This description applies to UN/LOCODE Web pages presentation only. For downloadable files in MS Access, CSV, or Text file formats, the "LOCODE" column is split into 2 columns (Country and Code) for easier use.

---

## 1️⃣ Column Descriptions

### 1.1 🔄 Column "Ch" (Change Indicator)

Every changed UN/LOCODE from the previous issue has a change indicator:

| Indicator | Meaning |
|-----------|---------|
| `+` | Added entry |
| `#` | Change in the location name |
| `X` | Entry to be removed in the next issue |
| `|` | Entry has been changed |
| `=` | Reference entry |
| `!` | US locations with duplicated IATA code, under review |

### 1.2 🌍 Column "LOCODE"

**Country Code (First 2 characters):**
- Indicates the country where the place is located
- Uses ISO 3166 alpha-2 Country Code
- Special case: "XZ" for international waters or cooperation zones

**Location Code (Last 3 characters):**
- Normally comprises three letters
- May use numerals 2-9 when letter permutations are exhausted

**Example:** `BEANR` = Antwerp (ANR) in Belgium (BE)

### 1.3 📝 Column "Name"

Place names are given in their national language versions using Roman alphabet characters.

**Diacritic Signs:**
- May be ignored (e.g., Göteborg → Goteborg, not Goeteborg/Gothenburg/Gotembourg)

**Multiple Language Versions:**
```
Abo (Turku)
Turku (Abo)
```

**Name Changes (Reference):**
```
Peking = Beijing
Leningrad = Saint Petersburg
```

**Alternative Name Forms:**
```
Flushing = Vlissingen
Munich = München
```

**Geographic Indicators:**
- `Bandung, Java`
- `Taramajima, Okinawa`

**Sublocations:**
- Format: `Main Location-Sublocation Type`
- Example: `London-Heathrow Apt`
- Cross-reference: `Heathrow Apt/London`

**Common Abbreviations:**
- `Apt` - Airport
- `I.` - Island(s)
- `Pto` - Puerto
- `Pt` - Port
- `St` - Saint

### 1.4 ✏️ Column "NameWoDiacritics"

Shows location names without diacritic signs.

### 1.5 🗺️ Column "SubDiv" (Subdivision)

Contains ISO 3166-2 administrative division codes (1-3 characters):
- State, province, department, etc.
- Country code prefix is omitted

### 1.6 ⚙️ Column "Function"

8-digit function classifier code:

| Code | Function |
|------|----------|
| `0` | Function not known/to be specified |
| `1` | Port (UN/ECE Recommendation 16) |
| `2` | Rail terminal |
| `3` | Road terminal |
| `4` | Airport |
| `5` | Postal exchange office |
| `6` | Multimodal functions, ICDs |
| `7` | Fixed transport functions (e.g., oil platform) |
| `B` | Border crossing |

### 1.7 ✅ Column "Status"

2-character status code:

| Code | Status |
|------|--------|
| `AA` | Approved by competent national government agency |
| `AC` | Approved by Customs Authority |
| `AF` | Approved by national facilitation body |
| `AI` | Code adopted by international organization (IATA or ECLAC) |
| `AS` | Approved by national standardization body |
| `RL` | Recognized location - confirmed by gazetteer |
| `RN` | Request from credible national sources |
| `RQ` | Request under consideration |
| `RR` | Request rejected |
| `QQ` | Original entry not verified since date indicated |
| `XX` | Entry will be removed in next issue |

### 1.8 📅 Column "Date"

Displays the last update/entry date for the location.

### 1.9 ✈️ Column "IATA"

The IATA code if different from the location code in LOCODE column.

### 1.10 🗺️ Column "Coordinates"

Geographical coordinates (latitude/longitude) in standard format:

**Format:** `DDDDlat DDDDDlong`

- **Latitude:** 4 digits + N/S (last 2 digits = minutes, first 2 = degrees)
- **Longitude:** 5 digits + E/W (last 2 digits = minutes, first 3 = degrees)

**Example:** `5120N 00415E`

### 1.11 💬 Column "Remarks"

General remarks regarding the UN/LOCODE:
- Creation information
- Change details
- Additional context

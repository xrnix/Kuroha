---
name: android-parser-engine
description: Android Scraping, Network Infrastructure & ML Kit Translation Specialist. Deep expertise in OkHttp client architecture (interceptors, DNS-over-HTTPS, Brotli, CookieJar), Jsoup DOM parsing, Cloudflare clearance handling, WebView scraping shims, ML Kit on-device Vision OCR, text block segmentation, and manga chapter download pipelines (atomic writes, CBZ compression). Use when developing, optimizing, or debugging network scrapers, site parsers, AI translation overlays, or offline manga download engines in Android.
---

# Android Parser Engine & ML Translation

Standards and engineering practices for high-resilience web scraping, network resilience, on-device OCR, and content translation in Android applications.

## 1. Network Stack & OkHttp Architecture
- **OkHttp Client Topology**:
  - Share a single root `OkHttpClient` instance across the entire application with a centralized `ConnectionPool` and `Cache`.
  - Derive source-specific clients via `rootClient.newBuilder()` to set per-domain timeouts, user agents, and custom interceptors without duplicating thread pools.
- **Header & Anti-Scraping Hygiene**:
  - Always emulate legitimate mobile browser headers: `User-Agent`, `Accept`, `Accept-Language`, `Sec-Ch-Ua`, `Sec-Fetch-Mode`, and dynamic `Referer`.
  - Maintain session cookies per-source using a persistent or in-memory `CookieJar` (preserving clearance cookies like `cf_clearance`).
- **Cloudflare & Anti-Bot Clearance**:
  - Detect 403 / 503 challenges with `cf-mitigated` or specific title tags.
  - Route challenged requests through a headless or invisible `WebView` with Javascript enabled to solve JavaScript challenges and extract updated cookies and user agents.
  - Use DNS-over-HTTPS (DoH) via Cloudflare/Google DNS to bypass local ISP-level DNS censorship.

## 2. Scraping & Parsing Patterns (Jsoup & JSON)
- **Defensive DOM Extraction**:
  - Always use fallback selectors when extracting manga titles, chapters, cover images, and pages (e.g., `doc.selectFirst("div.comic-info, div.manga-detail")`).
  - Clean URLs: Always resolve relative URLs against base URIs using `element.absUrl("href")` or `absUrl("src")`.
  - Handle lazy-loaded image attributes (`data-src`, `data-lazy-src`, `data-original`, `srcset`) before falling back to `src`.
- **Pagination & Throttling**:
  - Enforce respectful request rate limits (inter-request delays) to prevent source IP bans.
  - Implement exponential backoff retry interceptors for transient 429 and 5xx errors.

## 3. On-Device AI Translation & ML Kit Vision
- **OCR Pipeline**:
  - Use Google ML Kit Text Recognition (`TextRecognition.getClient(...)`) with Latin, Japanese, Chinese, or Korean script recognizers.
  - Pass hardware-managed `InputImage` from `Bitmap` or `ImageProxy`.
  - Segment recognized `Text.TextBlock` into bounding boxes (`Rect`), filtering out non-speech-bubble artifacts by aspect ratio, area, and text density.
- **In-Canvas Translation & Inpainting**:
  - Translate text blocks asynchronously using on-device ML models (ML Kit Translate) or remote translation APIs.
  - Typeset translated text inside original bounding boxes:
    - Draw opaque or speech-bubble-matching background pill/rect to mask original foreign text.
    - Measure text size dynamically using `StaticLayout` or `TextPaint` to fit within bounding bubble dimensions.
    - Render centered, readable typography with slight text shadow or contrast border for legibility against manga artwork.

## 4. Offline Storage & Download Pipelines
- **Atomic Downloads**:
  - Download chapter images into a temporary staging folder (`.tmp_chapter_id`).
  - Rename to the final destination folder or package into `.cbz` (ZIP format) only after all pages have been downloaded, verified for non-empty bytes, and validated as valid image formats.
- **Disk & Battery Optimization**:
  - Stream image network responses directly to disk without loading full byte arrays into memory.
  - Pause downloads gracefully when connectivity drops or power-saver mode activates.

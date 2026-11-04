# P06/P07 — User-selected image/PDF attachments

**Status (4 November 2026, SAST):** Initial P06 one-file UI and archive merged via PR #15; streamed larger-file import merged via PR #31. Core multiple-file encoding is proposed in PR #33 and is **not a multi-select Android UI yet**. No paid live OpenAI attachment request has been authorized or verified.

## Genuine implemented behavior

The visible paperclip in the native chat opens Android's standard **OpenDocument** picker with three allowed MIME types: JPEG, PNG or PDF. No blanket media/storage permission, camera access or hidden network download is requested. The selected provider file is opened only after explicit selection, copied directly to an app-private temp file using an 8 KiB byte-counted stream, and checked against JPEG/PNG/PDF magic bytes. The extension/display name is sanitized, a **16 MiB local data-URL transport safety limit** is enforced (the provider permits more, but the current base64 JSON path can duplicate Android memory), and an image thumbnail is decoded at bounded pixel size in the disk worker rather than in Compose.

One file may accompany one explicit, chargeable, text-prompt Send. The UI shows the image preview or filename, size and a remove button. A successful Send reserves a SQLite turn, retaining the private original with its filename, MIME and size. Future archive reopening shows the stored name and an **Open** control that exposes only that specific private file through an Android FileProvider with temporary view permission. Deleting a conversation also deletes its app-owned originals. The existing archive migrates non-destructively through **v4** (including pinned conversation state); it still stores just one media original per turn. The source file at the Android document provider is never modified or deleted.

The OpenAI Responses request contains a real `input_text` plus one `input_image` with base64 `data:image/jpeg/png` (detail=low), or one `input_file` with the PDF filename and `data:application/pdf;base64,` bytes. Local historical user/assistant pairs remain bounded and textual. **A previously attached image/PDF is NOT silently re-sent in future context; users must reattach it** if they want the model to inspect it again. No remote Files API upload, background media processing, image generation or OCR is performed; `store:false` remains the request default. Transport and UI tests use offline fixtures without accessing or spending on a live API.

## Safety and boundaries

- The selected image/PDF and its private original are **not encrypted at rest**; Android app sandbox protections and disabled automatic app backup apply. Avoid attaching secrets on shared/untrusted devices.
- The Android UI still selects one file with a typed prompt. **Multi-file picker/storage, office file types, camera capture, OCR and automatic image resizing** remain later work; the 16 MiB transport memory ceiling is a separate local limitation, not the provider's upload allowance. A PDF may incur image-processing input tokens; totals are shown only when the provider returns an actual usage receipt, not as a speculative price.
- No content of a file is parsed, displayed as HTML or executed locally; JPG/PNG thumbnails use the Android image decoder with bounded preview dimensions.
- Only a stored user-provided API key and a deliberate tap on Send can start a provider call; the redundant per-message authorization checkbox was removed in PR #27. Selection, previews and app restarts make no API call. The app preserves unsuccessful turns and never claims a failed request was completed.
- Android may not have a system PDF viewer installed, particularly on a hosted emulator. The archive reports that honestly rather than installing a viewer or uploading the file elsewhere.
- Source URI permissions are temporary: the app copies the original when selected. An imported draft can outlive a crashed process without being linked to a turn; orphan cleanup is a distinct follow-up acceptance gate.

## Verification gates

Required: current-source Kotlin/JVM HTTP loopback tests (exact multimodal JSON plus malicious filename and signature rejection), native Android build/lint/instrumentation compilation, hosted Android 15 tests using a real ContentResolver + internal FileProvider, private-original retention and cascade deletion, safe SQLite v1-to-v2 migration, Compose picker/chip viewer callbacks and large-font accessibility. Request Codex review for URI races, retention, limits and billing authority.

**Physical-phone accessibility of the new import path, a separately authorized live OpenAI multimodal smoke test, broad document formats, real multi-file UI, and full lifecycle/media cleanup** remain unverified. Do not announce Release 1 on the basis of source-level tests alone.

Official API shape references:
- https://developers.openai.com/api/docs/guides/images-vision
- https://developers.openai.com/api/docs/guides/file-inputs

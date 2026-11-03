# P06 — User-selected image/PDF attachments (first verified slice pending)

**Status:** Draft; branch `feat/p06-local-multimodal-attachments` stacked above P06 appearance PR #14, itself stacked on P06 settings #13 / P05 archive #12 / P04 chat #11. **No unapproved merge or live billable OpenAI request.**

## Genuine implemented behavior

The visible paperclip in the native chat opens Android's standard **OpenDocument** picker with three allowed MIME types: JPEG, PNG or PDF. No blanket media/storage permission, camera access or hidden network download is requested. The selected provider file is opened only after explicit selection, copied to an app-private directory using a byte-counted stream, and checked against JPEG/PNG/PDF magic bytes. The extension/display name is sanitized, a **2,000,000-byte hard limit** is enforced, and an image thumbnail is decoded at bounded pixel size in the disk worker rather than in Compose.

One file may accompany one explicit, chargeable, text-prompt Send. The UI shows the image preview or filename, size and a remove button. A successful Send reserves a SQLite turn, retaining the private original with its filename, MIME and size. Future archive reopening shows the stored name and an **Open** control that exposes only that specific private file through an Android FileProvider with temporary view permission. Deleting a conversation also deletes its app-owned originals. The existing v1 SQLite archive migrates to **v2 without dropping any conversations**. The source file at the Android document provider is never modified or deleted.

The OpenAI Responses request contains a real `input_text` plus one `input_image` with base64 `data:image/jpeg/png` (detail=low), or one `input_file` with the PDF filename and `data:application/pdf;base64,` bytes. Local historical user/assistant pairs remain bounded and textual. **A previously attached image/PDF is NOT silently re-sent in future context; users must reattach it** if they want the model to inspect it again. No remote Files API upload, background media processing, image generation or OCR is performed; `store:false` remains the request default. Transport and UI tests use offline fixtures without accessing or spending on a live API.

## Safety and boundaries

- The selected image/PDF and its private original are **not encrypted at rest**; Android app sandbox protections and disabled automatic app backup apply. Avoid attaching secrets on shared/untrusted devices.
- This first slice permits only one file and a typed prompt. **Larger documents, office files, gallery batches, camera capture, OCR and automatic image resizing** remain later work. A PDF may incur image-processing input tokens; totals are shown only when the provider returns an actual usage receipt, not as a speculative price.
- No content of a file is parsed, displayed as HTML or executed locally; JPG/PNG thumbnails use the Android image decoder with bounded preview dimensions.
- Only approved API key + one-use Send authorization can start a provider call. Selection, previews and app restarts make no API call. The app preserves unsuccessful turns and never claims a failed request was completed.
- Android may not have a system PDF viewer installed, particularly on a hosted emulator. The archive reports that honestly rather than installing a viewer or uploading the file elsewhere.
- Source URI permissions are temporary: the app copies the original when selected. An imported draft can outlive a crashed process without being linked to a turn; orphan cleanup is a distinct follow-up acceptance gate.

## Verification gates

Required: current-source Kotlin/JVM HTTP loopback tests (exact multimodal JSON plus malicious filename and signature rejection), native Android build/lint/instrumentation compilation, hosted Android 15 tests using a real ContentResolver + internal FileProvider, private-original retention and cascade deletion, safe SQLite v1-to-v2 migration, Compose picker/chip viewer callbacks and large-font accessibility. Request Codex review for URI races, retention, limits and billing authority.

**Physical-phone accessibility, a consented live OpenAI multimodal smoke test, broad document formats and full lifecycle/media cleanup** remain unverified. Do not merge the stacked PRs or announce Release 1 on the basis of tests that did not run.

Official API shape references:
- https://developers.openai.com/api/docs/guides/images-vision
- https://developers.openai.com/api/docs/guides/file-inputs

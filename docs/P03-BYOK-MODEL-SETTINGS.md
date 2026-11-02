# CiRYVA BYOK model profiles and Responses parameters

Verified against current official OpenAI documentation on **2026-11-02**.

## Deployment choice

CiRYVA is **freeware and distributable**, with **users' own OpenAI API keys (BYOK)**. No developer-shared OpenAI key is embedded or funded. Default request transport remains direct HTTPS from the user's device to `https://api.openai.com/v1/responses`. Using a common Cloudflare Worker to hold users' keys is **not necessary** for initial BYOK, and would create an extra party that handles each user's secret and chat content. If a proxy is later added for telemetry or authentication, it must never silently substitute the developer's key or upload user secrets without explicit disclosure.

**Security pending:** Android Keystore-backed protected storage, credential wipe/rotation, no logs, no backup, and a permissioned onboarding screen need implementation and review before connecting the real Send button. The current P03 implementation accepts a caller-owned bearer key in memory and does not store it.

## Model selector source of truth

`CiRYVAModelCatalog.selectable` is an audited snapshot of model IDs, incentive groups and supported control families. It includes:

- GPT-6 Luna (`gpt-6-luna`, standard incentive group); GPT-6.1 Sol and GPT-6 Astra; GPT-5.4 (`gpt-5.4-2026-03-05`).
- Smaller-model choices GPT-5.4 Mini (`gpt-5.4-mini-2026-03-17`) and GPT-5.6 Luna (`gpt-5.6-luna`).
- Legacy GPT-4o (`gpt-4o-2024-11-20`), GPT-4o Mini (`gpt-4o-mini-2024-07-18`), and o3 (`o3-2025-04-16`). The GPT-4o versions are scheduled for API retirement **2027-01-14**; warn users ahead of that date.
- The requested legacy o1 and o4-mini snapshots shut down **2026-10-23**. They remain visible only in `all` as **disabled retired references**, not selectable working models.

Always check the authenticated user's actual `GET /v1/models` access and provider error response before promising a model works for their key. This static catalogue does not grant entitlement.

## Request parameter matrix

`GenerationSettings` validates one model's supported settings and is attached optionally to `TextResponseRequest`. The transport writes `reasoning.effort`, `reasoning.summary`, `text.verbosity`, `temperature`, and `top_p` **only when the specific selected model/mode supports them**. GPT-4o and GPT-4o Mini permit sampling but not reasoning/verbosity fields. Legacy o3 permits low/medium/high reasoning, not the newer sampling controls. Newer GPT-5/6 families support verbosity and configurable effort; sampling is offered only with explicitly selected `none` effort for supported models. Do **not** enable temperature and top-p simultaneously.

`max_output_tokens` remains independently adjustable using `TextResponseRequest.maxOutputTokens` and is currently capped at **4,096 per turn** as the project's conservative prototype limit. This field includes visible and internal reasoning tokens. A future validated user setting may allow larger caps after robust accounting, budgets and cancellation are integrated.

Example generated Responses request body:
```json
{"model":"gpt-6-luna","input":"Hello","stream":true,"store":false,"background":false,"max_output_tokens":1024,"reasoning":{"effort":"low"},"text":{"verbosity":"medium"}}
```
No API key is included in the JSON; the private key belongs only in the HTTPS Authorization header.

## Complimentary tokens and critical privacy caveat

Official OpenAI help states that eligible **Build** organizations may receive **250,000 standard-group** and **2,500,000 smaller-group** tokens per day shared across each group. Eligible Launch/Grow organizations may receive **1 million** and **10 million** respectively. These are account-dependent **data-sharing incentives**, not free usage for every CiRYVA user. Users must enable sharing of their **API inputs and outputs** with OpenAI in organization data controls and maintain a positive API balance. Their data may be used to improve models. Keep this **disabled by default** in CiRYVA's UX; link the official data-controls dashboard and show its privacy consequence. CiRYVA cannot alter organization data-sharing controls through the chat API. The allowance resets **00:00 UTC**, and an entire request can be charged if it crosses the remaining allowance; tools and ineligible models are charged separately.

Never display a counter as 'remaining free tokens' without verified authoritative organization usage and enrollment. Show local provider-reported usage and an estimated cost only where applicable, clearly distinct from billing.

Sources: https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai ;
https://developers.openai.com/api/docs/models ;
https://developers.openai.com/api/docs/deprecations ;
https://developers.openai.com/api/docs/guides/latest-model .

## Next step

Add secure Android BYOK key entry + clear key + non-backup storage; a native model/settings panel reading `CiRYVAModelCatalog` through the Android module; and an approved live API test with the user's key and explicit spending permission. This PR's payload tests use a **local HTTP loopback**, with no paid OpenAI calls.

### Model-specific summary compatibility (Codex review)

OpenAI's Responses schema restricts `reasoning.summary: concise` to newer reasoning models, not legacy o3. `CiRYVAModel.summaryModes` now independently lists summary modes: o3 permits `auto` and `detailed`, while compatible GPT-5/GPT-6 reasoning models additionally offer `concise`. The validator rejects the unsupported o3 combination before reaching HTTP; a regression test documents it. Revalidate model modes before release as OpenAI changes them.

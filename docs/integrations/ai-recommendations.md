# AI recommendations (Claude or Gemini)

The home page section **"Picked for you nearby"** shows offers worth knowing about around the visitor: café discounts,
free health check-up camps, gym packages such as "pay for 6 months, get 6 months free", free trial classes and more.

## How it works

1. The browser sends the visitor's location (GPS, or their chosen city) and an optional request such as "cheap gym".
   The location is used for that request only and is not stored.
2. The backend collects **real offers** within 8 km from the database: offers businesses publish (owner page → *Offers*
   tab), restaurant discounts, and free trial classes or assessments.
3. If an API key is configured, the AI (**Claude** or **Google Gemini**) chooses up to six of them for this person and moment and writes a short reason
   for each, answering in structured JSON that refers to offers by id. The backend drops any id that isn't a real offer,
   so the AI can't invent deals.
4. Without an API key, or if the AI call fails, a simple rule ranks the same offers (free and big savings first, then
   rating, minus distance, at most two per category). The page works either way and says which method was used.

Answers are cached for 15 minutes per area, request, language and hour, and each visitor gets at most 30 fresh AI
answers an hour (after that the rules take over), so costs stay predictable.

## Choosing a provider

`AI_PROVIDER=auto` (the default) uses Claude when `ANTHROPIC_API_KEY` is set, otherwise Gemini when `GEMINI_API_KEY` is
set, otherwise the rules. Set `AI_PROVIDER=claude` or `AI_PROVIDER=gemini` to force one. Both get the same instructions
and must answer in the same JSON format, and both are checked the same way.

## Getting a Gemini API key

1. Go to <https://aistudio.google.com>, sign in with a Google account and open **Get API key → Create API key**.
2. Put it in `.env` (never commit it, never paste it in chat or tickets):
   ```properties
   GEMINI_API_KEY=...
   ```
3. Restart the backend. The log shows `AI recommendations: on, Gemini model gemini-3.5-flash-lite`.

The default Gemini model is `gemini-3.5-flash-lite` with low thinking: in testing it answered in 2–4 seconds. Larger
models (for example `gemini-3.8-flash`, which Google suggests for new projects) can be much slower when demand is high;
the app gives up after 25 seconds and uses the rules. Older models such as `gemini-2.5-flash` are no longer available to
new keys. Change the model with `GEMINI_MODEL`; if a model rejects the thinking setting, the app retries without it.
Check current prices and free-tier limits at <https://ai.google.dev/pricing>, and set a budget alert in Google Cloud.

## Getting a Claude API key

1. Go to <https://console.anthropic.com> and create an account (or sign in).
2. Add credit under **Settings → Billing**. API usage is pay-as-you-go and separate from any Claude.ai subscription.
3. Open **Settings → API Keys → Create Key**, name it `BookEasy`, and copy the key (it starts with `sk-ant-`). It is shown
   only once.
4. Put it in `.env` (never commit it):
   ```properties
   ANTHROPIC_API_KEY=sk-ant-...
   ```
5. Restart the backend. The log shows `AI recommendations: on, model claude-opus-5-5`.

Set a monthly spend limit in the Console (**Settings → Limits**) so a bug or traffic spike can never run up a large bill.

## Claude model and cost

The default model is **Claude Opus 5.5** (`claude-opus-5-5`), run at low effort because the task is choosing from a
short list and writing one sentence each. Pricing is $4 per million input tokens and $20 per million output tokens; a
recommendation request is roughly 2,000–4,000 input tokens and 300–1,000 output tokens (including the model's brief
thinking), so about **₹2–3 per fresh answer** (cached answers are free).

To spend less, set `AI_MODEL` in `.env` to a smaller model, for example `claude-haiku-5-5` ($0.10 / $0.50 per million
tokens, roughly a hundredth of the cost) and compare the results on your own data.

On models that support it (Opus 5.5, Opus 5, Fable 5.1, Sonnet 5.5), a request Claude declines is retried by the API on a
suitable fallback model (`server-side-fallback-2026-07-01`, `fallbacks: "default"`); otherwise, or if there is still no
answer, the rules are used.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | empty | Claude API key; empty means rule-based ranking only |
| `AI_MODEL` | `claude-opus-5-5` | Claude model |
| `GEMINI_API_KEY` | empty | Google Gemini API key |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Gemini model |
| `GEMINI_THINKING_LEVEL` | `low` | Gemini thinking level (empty to leave it to the model) |
| `AI_PROVIDER` | `auto` | `auto`, `claude` or `gemini` |

Tests never call the real API (`bookeasy.ai.api-key` is empty in `application-test.yml`).

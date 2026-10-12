# 0002. Hybrid summarization: Gemini Nano on the device, Claude as the fallback

- Status: accepted (the Nano quality result is an open decision, see Limitations)
- Date: 2026-10-07

## Context

"Summarize my notes on a topic" is a bounded task: a few retrieved chunks in, three bullet points out.
That fits a small on-device model, which is free per call, works offline for the generation step and keeps
the summary text off the network. But on-device models are not available everywhere:

- Gemini Nano is reached through AICore and ML Kit GenAI, and only some phones offer it. On a Galaxy S25
  it works; on a Galaxy S23 and on the emulator every call fails with `AICORE_INCOMPATIBLE`.
- Even on a supported phone it can be unusable at a given moment: AICore disabled in settings (error 601),
  model not downloaded yet, model busy, or a request the model refuses.
- The model has hard input limits and quirks that the documentation did not predict (see Evidence).

Claude through the proxy works on every device but costs tokens and needs the network.

## Decision

1. **Use the ML Kit Summarization API, not the Prompt API.** It is available on more devices (the S25 is
   listed; the Prompt API is listed for newer Pixels and a few other brands) and it is tuned for exactly this
   task: fixed input types, up to three bullets.
2. **Put the choice in the domain layer.** `OnDeviceSummarizer` and `CloudSummarizer` are interfaces in the
   pure-Kotlin `domain` module, and `SummaryRouter` decides between them. Implementations (`NanoSummarizer`,
   `ClaudeSummarizer`) live in `rag`. The router is deterministic, so every path is unit-tested with fakes.
3. **Two explicit entry points, not intent detection.** The user picks Ask or Summarize. Ask goes to the
   Claude agent loop with the retrieval tool; Summarize retrieves chunks and goes through the router.
4. **Router rules.**
   - Check availability first; a model that is downloadable or downloading is not used yet.
   - Accept only input of 400 to 7860 characters (measured on the S25).
   - Retry `BUSY` three times (after 1 s, 2 s and 3 s), then fall back. After a finished call the model stays busy for
     more than 1.5 s and was free by 3 s in 36 of 36 waits on the S25.
   - Any other failure falls back to Claude and carries a `FallbackReason` that the UI shows.
   - Remember a verdict for the whole session only when the device is unsupported
     (`AICORE_INCOMPATIBLE`). Every other reason is fixable (enable AICore, finish the download), so it
     must not lock the on-device model out.
5. **Both engines get the same text.** The chunks of the single best-matching note are taken best-first (at most
   five, within the limit), then ordered by position. Blending chunks of unrelated notes made Nano state false
   things in the first evaluation. If even the best chunk scores below 0.35, nothing is summarized and the UI
   says no notes were found.
6. **Use the plain `runInference` call, never the streaming overload.**
7. **Show where the summary came from.** A badge says "On-device · Gemini Nano" or "Cloud · Claude", and a
   cloud summary explains why the on-device model was not used.
8. **Make the comparison measurable.** The router can be pinned to one engine (`ON_DEVICE_ONLY`,
   `CLOUD_ONLY`) and times the summarizer call, which is what the evaluation uses.

## Alternatives considered

- **Always Claude.** Simplest, but there would be nothing on-device to show, and every summary costs tokens.
- **On-device only.** Rejected: most phones, including the S23, would get no summaries at all.
- **Prompt API.** Free-form and more flexible, but available on fewer devices (not the S25), and the task
  does not need flexibility.
- **Detect intent from the text** to choose Ask or Summarize. Non-deterministic and hard to test; two
  explicit modes are clearer to the user too.
- **Fall back silently, without a badge.** Rejected: the privacy and cost difference between the engines is
  the point of the feature, and the user should see which one ran.
- **Remember every failure for the session.** Rejected: a disabled AICore or a pending download would hide
  a working on-device model until the app restarts.

## Consequences

- Summarize works on every phone; on supported phones the generation is free and local.
- More moving parts: two engines, eleven fallback reasons, a router. This is paid for by tests, one per
  reason.
- The on-device path is slower in the measurements: median 2.8 s against 1.7 s for Claude on 2 to 5 chunks, and
  9.2 s against 2.3 s on 7 chunks.
- **"Notes never leave the phone" is not true.** Only the generation stays local. Embedding the topic and
  the text of saved notes still goes to Voyage through the proxy, so the privacy claim must say exactly that.
- A user whose AICore is disabled has to switch it on themselves; the app only explains how.

## Evidence (measured)

| Fact | Value |
|---|---|
| Input accepted by the on-device model (`ARTICLE`) | 400 to 7860 characters (the documentation says about 4000 tokens) |
| Plain call vs. streaming call on the same text (S25) | plain works; streaming fails with a policy-check error (code 11) |
| Model warm-up (S25, `nano-v1`) | about 3 s |
| On-device summary time (S25) | 3.9 to 5.1 s for 2-3.5 thousand characters |
| Claude summary time through the proxy | median 1.7 s (45 runs, 2 to 5 chunks); 2.3 s on 7 chunks |
| Galaxy S23 and emulator | `AICORE_INCOMPATIBLE (-101)` on every call |
| Galaxy S25 with AICore disabled | `601 BINDING_FAILURE`, fixed by enabling AICore |
| A simple text about the Moon | `RESPONSE_GENERATION_ERROR (15)` in both call modes |

Quality, measured on 15 topics (target set before measuring: 90% passing cases; a case passes when nothing is
invented or distorted and at least two of three key points are covered):

| Engine | Runs | Passed | Median time (2-5 chunks) |
|---|---|---|---|
| Claude, topic passed in `<topic>` | 45 (3 per topic) | 41 (91%) | 1.7 s |
| Gemini Nano, text only | 15 (1 per topic) | 6 (40%) | 2.8 s |

Further facts from the same run: Nano never reported `BUSY` with the 1/2/3 s router delays (0 retries in 15 runs);
15 topics gave 9 distinct Nano outputs because it does not see the topic; with 7 chunks (first evaluation, an older
knowledge base) Nano took 9.2 s against 2.3 s for Claude. The scorer was Claude, which may favor its own family.

## Limitations and open points

- The ML Kit GenAI APIs are in beta; limits and behaviour may change between versions.
- English, Japanese and Korean only.
- Error code `NOT_AVAILABLE (8)` has not been seen on a device; it is treated as fixable.
- A relevance floor of 0.35 (`MIN_RELEVANCE`) was added after the evaluation; below it Summarize reports that no notes
  were found. Topics between 0.35 and the lowest real topic (0.54) still get a summary of the nearest chunks.
- **Open decision: Nano quality.** At 40% against the 90% target, Nano as the default on supported phones is a
  choice for cost, latency and local generation, not for quality. Options: keep it with a warning, make Claude
  the default and Nano opt-in, or only use Nano when the retrieved chunks clearly match the topic.
- Nano receives no topic, so on an unrelated topic (for example a Russian sentence about London) it summarizes the
  nearest chunks. Measured best scores (voyage-4, same numbers as in the app): topics found in the notes 0.54 to
  0.82 (also in Russian: 0.63 to 0.77), near-miss unrelated topics 0.38 to 0.46 (Gradle, iOS push), blatant junk
  0.13 to 0.29 (London, a name, the weather, pancakes). A floor between 0.46 and 0.54 would separate all of them
  except the one retrieval miss (`expect/actual`, 0.27). With only 15 chunks the margin is thin, so a floor of
  about 0.35 to 0.40 looked safe. A check on the device then showed that short queries typed by a person score lower
  than the descriptive evaluation topics: "clean architecture" 0.39, a phonetic Russian spelling of "clean architecture"
  0.33, the proper Russian phrase for "clean architecture" 0.50, the Russian phrase for "mobile application security"
  0.62. The floor therefore sits close to real queries.
- Voyage `input_type` (documents as "document", topics as "query") was tried and rolled back: no improvement.
- Tested devices: Galaxy S25, Galaxy S23 and an emulator.
- Free-form chat on the device is out of scope; on the S25 it would need an open model rather than Nano.

# 0003. Secrets stay on the server: a serverless proxy reads them from SSM

- Status: accepted
- Date: 2026-10-08

## Context

The app calls two paid APIs, Claude and Voyage. The first version read both API keys from `BuildConfig`, which
means they are compiled into the APK. An APK can be unpacked in minutes, so anyone with the file could extract
the keys and spend the owner's money.

Anything the app ships with is public, including the address of a backend. A solution therefore has to limit
what a leaked address can do, not only hide a secret.

## Decision

1. **Move the keys out of the app into a small serverless proxy** (API Gateway HTTP API and a Python Lambda,
   deployed with SAM). The app sends the same request body it used to send to the provider; the proxy adds the
   authorization header itself. The app only knows the proxy address, which is kept in `~/.gradle/gradle.properties`
   and read into `BuildConfig` so that it is not committed to the public repository.
2. **Store the keys in SSM Parameter Store as SecureStrings**, not in the template or in environment variables
   (environment values are visible in the console and tend to end up in logs or commits). The function reads
   them once per container and caches them, so SSM is called only on a cold start.
3. **Give the function the smallest permission it needs**: `ssm:GetParameters` on exactly the two parameter ARNs.
   There is no KMS statement: the parameters use the default AWS-managed key (`alias/aws/ssm`), and the function
   still loads both keys without one. This was verified, not assumed: the statement was removed from a
   deployed stack and the keys were read from fresh containers (a changed environment variable forces them,
   because warm containers hold the keys in memory and would hide a missing permission). A customer-managed
   key would need `kms:Decrypt` added.
3a. **Give the deploy user only what the stack needs.** The IAM user that runs `sam deploy` holds one customer-managed
   policy scoped to this stack (the stack, its Lambda function, API and log group, and its roles; only the Lambda
   logging policy can be attached to those roles), plus two narrow inline policies (the SAM bucket and the two SSM
   parameters). It started with five AWS-managed full-access policies, among them `IAMFullAccess`, which would let a
   leaked key grant itself anything. The broad ones were removed one at a time with a deploy after each, and the last
   check was a real change that updated the role, the function and the API. The user can no longer change its own
   permissions; that takes the root user.
4. **Limit what a leaked proxy address can do**, since the address is in the APK: a fixed model per provider,
   a field allowlist, a cap on output tokens, request size limits, no server-side tools such as web search,
   rate limiting on the stage, and spending caps as the real backstop: a monthly limit at Anthropic ($5, with an
   email alert at $4), no payment method at Voyage so its free allowance is the cap, and an AWS zero-spend budget
   that emails on the first charge.
5. **Do not authenticate callers, and say so.** The production answer would be Play Integrity, which proves the
   request comes from the genuine app on a real device. It is not implemented because the app is not published
   to Google Play.

## Alternatives considered

- **Keep the keys in the APK, obfuscated.** Rejected: obfuscation only slows extraction down.
- **Environment variables on the function.** Rejected: values appear in the console and in the template.
- **A shared secret in a request header.** Rejected: it would also have to ship in the APK, so it adds
  nothing.
- **A customer-managed KMS key for the parameters.** Not needed at this scale; it would add cost and a
  permission to maintain.
- **Per-user accounts and tokens.** Out of scope for an unpublished demo app.

## Consequences

- No provider key is in the APK or in the repository; rotating a key means updating a parameter.
- Anyone who learns the address can still call the proxy within its limits, so spending is bounded by the limits
  and by the provider-side spend caps, not by authentication.
- Every request has one extra network hop. Measured cost is small: warm calls through the proxy took
  roughly 130 ms inside Lambda, against hundreds of milliseconds to seconds for the model itself.
- Prompts and note text now pass through the owner's AWS account (the proxy does not log request or response
  bodies).

## Limitations

- Throttling in API Gateway is best effort, not a hard cap.
- Cached keys change only after a cold start, so a rotated key can take effect late.
- Because the proxy forwards only an allowlist of fields, any new request parameter (for example a Voyage
  `input_type`) needs a proxy change and a redeploy.

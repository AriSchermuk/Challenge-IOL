# Design

## Problem and scope

Implement a correct token bucket rate limiter and demonstrate it protecting a Java
HTTP endpoint. The owner selected Java 21, Spring Boot MVC, one process, one policy,
per-client locking, startup configuration, a hard tracking bound, Docker packaging,
and comprehensive tests with coverage reporting. Product outcomes are in the
[business requirements](docs/business/REQUIREMENTS.md); the exact contract is in
[SPEC.md](docs/technical/SPEC.md). This document describes the implemented design.

The [README diagrams](../README.md#diagramas) visualize the architecture, admission
flow, and client concurrency described below.

## Request flow

`Application` starts Spring. Validated `LimiterProperties` binds startup settings.
`LimiterConfiguration` creates the core with a monotonic clock and logs the policy.
`WebConfiguration` installs `AdmissionInterceptor` for resolved `/api/**` handlers.

The interceptor guards methods, obtains an `Optional<String>` from `readClientId`,
and calls `RateLimiter.acquire`. `writeDecision` translates the decision to HTTP;
`writeError` writes the JSON error contract.
It allows the controller to execute, or returns 400/429/503 with a simple JSON error.
Retry-After is upward-rounded whole seconds. `DemoController` returns a small success
response; health is outside the protected path. `ApplicationErrors` logs unexpected
application failures and returns a generic 500 without exposing exception messages.
Error dispatch is not charged again. HEAD is explicitly blocked before the demo
controller because Spring otherwise maps it through GET; OPTIONS is metadata only.

The core package imports neither HTTP nor Spring classes. It can be used directly
from Java without the web adapter. There is no external store, background refill
worker, queue, or third-party limiter library.

## Token accounting

One token is 1,000,000,000 integer credit units. For an integer rate R tokens/second,
one elapsed nanosecond adds R units. Available credit is capped at capacity times
the scale. Admission subtracts one scale; rejection preserves credit and returns
the upward-rounded delay to one token.

Before multiplying elapsed time by the rate, compare against the time needed to
reach capacity. This caps long idle intervals safely and avoids overflow. The
configured bounds keep maximum credit at 10^15. Excess time while full is discarded.
Tests control ticks; production uses System.nanoTime rather than calendar time.
A negative tick difference grants no refill and leaves the reference unchanged.
Monotonic counter wrap is supported through subtraction for intervals below 2^63 ns.

The names `availableCredit`, `maximumCredit`, `refillTokensPerSecond`, and
`lastAccountingNanos` make units and responsibilities explicit.

Integer credit avoids floating-point tolerances at exact admission boundaries.
The cost is explicit scale/rounding/overflow reasoning. A refill thread would add
scheduling and shutdown concerns without improving these lazy calculations.

## Per-client concurrency and bounded state

The `clientEntries` registry is a HashMap protected by `registryLock`. Each
`ClientEntry` has a `bucketLock`, `tokenBucket`, and `activeOrWaitingRequests` count. Registry admission increments the
count before releasing the registry lock, so references include callers waiting
for the client lock. Accounting then takes only that client lock. It releases the
client lock before calling `unpin` in an outer finally; `unpin` decrements the count
under the registry lock. `pin` returns `Optional<ClientEntry>`; an empty result maps
to tracking saturation, keeping this absence explicit without a null return.

When full, registry admission scans for an unreferenced bucket, tries its lock
without waiting, refills it, and removes it only if full. Removal and replacement
happen while the registry is locked. The entry lock is always released. A depleted
bucket cannot be removed because recreating it full would reset its quota.

This prevents two live buckets for one identity, bounds creation atomically, and
keeps one client's accounting independent of another's. Requests never acquire the
registry while holding a client lock; cleanup never waits for a client lock under
the registry. Exceptions release references/locks. Tests pause accounting with a
controlled clock and verify another client progresses, cleanup cannot detach an
active bucket, and failed clock calls do not leak references.

The tradeoff versus a single global accounting lock is extra lifecycle coordination.
The registry still serializes lookups/pinning briefly; cleanup is O(N) under the
registry lock and can delay other lookups. This is an explicit scaling limit. No
claim of strict fairness or benchmark throughput is made.

## Capacity policy and trust

Default capacity 10, refill 2 tokens/second, and 10,000 tracked clients are business
choices configurable at startup. At tracking saturation, new identities receive
503 if no safe entry can be reclaimed. Existing client buckets remain usable.
Increasing supported usage or changing rejection behavior requires a business and
resource review. Automatically evicting depleted entries or failing open would weaken
quota enforcement and is not the selected behavior.

Client-ID syntax validation does not authenticate the caller. Production IDs must
be pre-approved/validated by a trusted traffic layer that replaces spoofable headers
and prevents direct application access. Implementing that layer is out of scope.

## Build, tests, and deployment

Maven's conventional lifecycle fits the small Java service; Gradle offers more
flexible tasks and useful caching features but no measured need justified them here.
The wrapper pins Maven and its distribution checksum. Boot manages compatible
framework/test dependencies; explicit versions and official sources are in SPEC.md.

Surefire runs unit/concurrency tests; Failsafe runs HTTP tests on an embedded server.
JaCoCo instruments both executions, merges data, and reports all application code.
Tests verify behavior and invariants, not private field layouts. Controlled clocks
and bounded latch/future waits replace timing-dependent sleeps in concurrency tests.
Test-only failure endpoints are in test sources and are absent from the packaged JAR.

Docker uses a Java build stage that runs verification and non-root JRE runtime stage, both pinned
by registry digest. Docker image build, all 17 tests inside Linux, non-root runtime behavior, graceful
shutdown, and coverage export have been verified on the owner-authorized local
Colima engine. The builder installs unzip so the wrapper downloads the ZIP matching
its pinned checksum rather than falling back to a tar archive. Java builds,
packaged-JAR smoke checks, measured coverage, and remaining gaps are recorded in [VERIFICATION.md](docs/technical/VERIFICATION.md).

## Future extensions

- Shared state or coordinated ownership across application instances would preserve
  one allowance per identity while spreading traffic and allowing instance replacement.
  It requires atomic distributed operations, a shared time model, timeout/outage
  policy, and new failure/contended-instance tests; merely copying the process is insufficient.
- Configurable algorithm selection could provide fixed/sliding window, leaky bucket,
  and other behaviors. Their configuration, memory costs, guarantees, and state
  migration need an explicit design. This version implements token bucket only.
- Client/tier or endpoint-specific policies and live policy changes could be added
  if needed or more time is available. Real APIs with operations of different costs
  would likely want endpoint policies. Health remains an operational exemption.
- Metrics, CI, and coverage thresholds are deferred owner decisions, not implemented
  features. Public repository publication is now owner-authorized. Submission remains
  owner-controlled.

## AI use and understanding

The owner selected scope and meaningful tradeoffs. The assistant drafted the plans,
implemented code and tests, researched official tooling sources, ran verification,
and updated documentation. The owner delegated routine version/setup choices and
later authorized progress without routine phase approvals when the plan is followed.
New meaningful deviations still require an owner decision.

AI-generated code is subject to the same tests and review as other code. Coverage
is evidence of execution, not a correctness proof. The owner has not yet completed
the final code-understanding review. No particular function has been identified as
not understood; if any is identified, explain it and add the explicit comment required
by the challenge if it remains unclear before submission. Do not claim understanding
on the owner's behalf.

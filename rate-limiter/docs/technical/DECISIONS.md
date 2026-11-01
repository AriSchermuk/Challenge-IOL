# Technical decisions and tradeoffs

This record explains the implemented design. Exact contracts and tool versions
are in [SPEC.md](SPEC.md); measured evidence is in [VERIFICATION.md](VERIFICATION.md).

| Decision | Rationale | Tradeoff / alternative |
| --- | --- | --- |
| Java 21, Spring Boot MVC | Familiar Java service structure, validation and HTTP integration testing | More framework dependencies than the JDK HTTP server |
| Embedded demo API | Demonstrates enforcement in one runnable application; core stays independent of HTTP | A standalone service would support callers in other languages but add network latency and failure handling |
| Single process, in-memory state | Focuses the prototype on correctness, concurrency, and bounded tracking | Restart resets quotas; multiple independent processes multiply allowances |
| Token bucket | Supports a bounded burst and sustained replenishment | Fixed/sliding windows provide different timing guarantees and resource costs |
| Integer credit and monotonic time | Preserves fractional replenishment without floating-point tolerances; calendar changes do not affect quota | Requires explicit units, rounding, and overflow guards |
| Single startup-configured policy | Independent client buckets with predictable configuration | Client tiers, route policies, and live updates require lookup/migration semantics |
| Charge on admission | Limits attempted work and keeps admission independent of endpoint outcomes | Downstream failures still spend tokens; refunds require coordination |
| X-Client-Id header | Easy to demonstrate independent client quotas | Syntax validation is not authentication; a production traffic layer must validate identities and prevent spoofing |
| Per-client locking | Same-client decisions are atomic while different clients can account concurrently | Creation/removal and the registry bound require additional lifecycle coordination |
| Registry references include waiting requests | Prevents reclamation while a request holds or is waiting to use an entry | Reference release must happen on every exceptional path |
| Hard tracking bound; safe on-demand reclamation | Bounds memory without a maintenance thread | O(N) scans can delay registry access; new identities receive 503 when no unused full bucket can be reclaimed |
| 429 with Retry-After | Gives quota rejection and retry guidance a clear HTTP contract | Guidance does not reserve future capacity |
| Health exemption and method guards | Operational checks and unsupported requests do not spend quota | HEAD needs explicit guarding because MVC normally maps it through GET |
| JUnit unit/concurrency/HTTP tests | Exercises algorithm boundaries and observable behavior | Real-time Python demonstrations complement rather than replace controlled-clock tests |
| JaCoCo line/branch reporting | Makes coverage gaps visible across application code | No numerical threshold; high coverage alone does not prove correctness |
| Multi-stage Docker, non-root runtime | Builds/tests inside Linux and ships only runtime artifacts | Requires an engine and network downloads; builder needs unzip for the pinned Maven ZIP |
| Selective Optional and method extraction | Explicit missing-result contracts and cohesive HTTP/lifecycle responsibilities | Lock and iterator paths remain imperative for clarity |

## Maven versus Gradle

Maven's standard lifecycle fits this small Java service without custom build
orchestration. The wrapper pins Maven and its distribution checksum.

| Aspect | Maven | Gradle |
| --- | --- | --- |
| Configuration | Explicit XML POM; more verbose | Kotlin/Groovy build scripts |
| Execution | Ordered lifecycle phases with plugin goals | Task dependency graph with plugin conventions |
| Custom workflows | Can be cumbersome for unusual tasks | More flexible task composition |
| Repeated builds | Sufficient baseline for this project | Up-to-date checks and optional task-output caching |
| Tool version | Maven Wrapper | Gradle Wrapper |

No build-performance comparison has been measured for this project. A wrapper alone
does not make every build input reproducible; dependencies, plugins, JDK, and image
versions also matter.

Sources: [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html),
[Maven Wrapper](https://maven.apache.org/wrapper/),
[Gradle scripts](https://docs.gradle.org/current/userguide/writing_build_scripts.html),
[Gradle caching](https://docs.gradle.org/current/userguide/build_cache.html),
[Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

## Capacity as a business policy

Defaults are capacity 10, refill 2 tokens/second, and 10,000 tracked clients,
configurable at startup. The client bound and saturation response are business
policies, not inherent properties of token bucket. Raising supported usage requires
resource review. The implementation neither grows automatically beyond the bound
nor bypasses limiting at saturation. Recreating a depleted bucket would reset quota,
so reclamation is restricted to full buckets without active/waiting requests.

## Extension points

Multiple-instance coordination needs a shared source of truth or coordinated
ownership, atomic cross-instance updates, a time model, and an outage policy.
Configurable algorithms need explicit parameters, guarantees, and state migration.
Per-client/endpoint policies and live changes need lookup and balance-update rules.
See [future extensions in DESIGN.md](../../DESIGN.md#future-extensions).

The [Python runner](../../scripts/probar_api.py) provides a visible end-to-end demo;
its reports are generated artifacts. Local Docker tooling is described separately
in [LOCAL-ENVIRONMENT.md](LOCAL-ENVIRONMENT.md). CI, metrics, and a coverage threshold
are outside the current implementation.

# Rate limiter specification

Status: approved and implemented. Java tests, Docker image build/runtime checks,
and coverage export pass. Local Docker engine is installed and running.
See [VERIFICATION.md](VERIFICATION.md) for measured evidence and remaining gaps.

The owner's final batch approved integer accounting and bounds, registry references
with per-client locks, safe reclamation, exact identity/HTTP behavior, line/branch
coverage, and delegated tooling/setup choices. This document includes the resulting
technical selections. Implementation was authorized against this contract.

Business context: [requirements](../business/REQUIREMENTS.md).
Decision authority: [decision register](DECISIONS.md).

## Requirement traceability

The contract below remains the approved behavior. Evidence links describe actual
implementation and tests; verification gaps are not marked complete.

| Requirement | Implementation | Verification | Status |
| --- | --- | --- | --- |
| B1: one Java process | [Application](../../src/main/java/com/iol/challenge/ratelimiter/Application.java) | Packaged JAR smoke; documented reset semantics | Java verified |
| B2: token bucket | [TokenBucket](../../src/main/java/com/iol/challenge/ratelimiter/core/TokenBucket.java) | [Algorithm tests](../../src/test/java/com/iol/challenge/ratelimiter/core/TokenBucketTest.java) | Verified |
| B3: independent clients | [RateLimiter](../../src/main/java/com/iol/challenge/ratelimiter/core/RateLimiter.java) | [Registry tests](../../src/test/java/com/iol/challenge/ratelimiter/core/RateLimiterTest.java) | Verified |
| B4: rejection guidance | [Interceptor](../../src/main/java/com/iol/challenge/ratelimiter/web/AdmissionInterceptor.java) | [HTTP tests](../../src/test/java/com/iol/challenge/ratelimiter/ApplicationIT.java) | Verified |
| B5: concurrent clients | RateLimiter per-client locks and registry references | Registry concurrency tests | Verified |
| B6: bounded tracking | RateLimiter admission and safe reclamation | Registry saturation/creation/reclamation tests | Verified |
| B7: reviewable deliverable | [README](../../README.md), [DESIGN](../../DESIGN.md), sources and tests | [Verification](VERIFICATION.md) | Understanding/submission review remains |
| B8: embedded API | Interceptor and demo controller | HTTP tests and actual JAR smoke | Verified |
| B9: packaging/coverage | [Dockerfile](../../Dockerfile), [POM](../../pom.xml), logging configuration | Native/Linux tests, measured coverage, image/runtime/shutdown and export checks | Verified |

## Objective

Build a runnable Java HTTP application that restricts requests independently by
client, behaves correctly under concurrent access, and has deterministic tests.
The owner should be able to explain the algorithm, state lifecycle, synchronization,
HTTP behavior, and limitations.

## Scope boundaries

Included: one protected demo endpoint, one policy, in-memory client buckets,
bounded state, HTTP error responses, unit/concurrency/integration tests, logging,
Docker packaging, coverage reporting, and docs.

Excluded pending explicit scope changes: distributed enforcement, persistence,
authentication, multiple algorithms, multiple policies, live configuration,
request queuing, dashboards, and performance/availability guarantees.

Restarting the process resets all buckets. Running two instances creates two
independent allowances. These limitations must appear in the final README.

## Future directions requested by the owner

### Multiple application instances

Eventually support multiple application instances enforcing one shared allowance
per client. This would allow traffic to be spread across instances without
multiplying a client's quota, and allow an application instance to be replaced
without necessarily losing limiter state. Additional instances can improve
application capacity and availability, provided the shared state layer is also
designed to meet those needs; neither improvement is automatic.

This requires a shared source of truth or coordinated ownership for each client's
state. Refill, check, and consumption must remain atomic across instances; a Java
lock protects only its own process. A shared store such as Redis is a candidate,
not an approved dependency. A future design must decide the shared time model,
state expiry, consistency, timeouts, and whether to allow or reject requests when
the state service is unavailable. Process-local monotonic timestamps cannot simply
be compared across machines. Add tests for cross-instance contention, instance
restarts, and shared-state failures before claiming distributed correctness.

This direction is documented for future work; the initial implementation and its
acceptance criteria remain single-process.

### Configurable algorithm selection

Eventually allow operators to choose a rate-limiting algorithm through configuration.
The desired direction is broad algorithm support; the supported set must be made
explicit in a future specification. Candidates include token bucket, fixed window,
sliding window log, sliding window counter, and leaky bucket.

Selection would let deployments choose burst tolerance, strictness of trailing-window
limits, approximation, and memory cost to suit their workload. Algorithms have
different parameters and guarantees; changing the selected algorithm is not merely
changing the token bucket's capacity or refill rate. Leaky bucket variants may
also require deciding whether requests are rejected or queued for paced execution.

Future work must define each algorithm's configuration and tests, how configuration
selects an implementation, and how existing client state is reset or migrated.
Whether changes require restart or can happen live remains a future decision.
For this prototype, implement token bucket only. Keep this roadmap in DESIGN.md;
any extension interfaces or strategy abstractions require review before adoption.

### Identity validation and policy flexibility

For a real deployment, a trusted traffic layer should validate/pre-approve client
identities and supply the header. It must prevent callers from spoofing an approved
ID, for example by replacing incoming identity headers after authentication and
restricting direct access to the application. Building that traffic layer is outside
this prototype. Here, a syntactically valid ID is accepted as a trusted demo input;
syntax validation alone does not prove identity approval.

Different policies by client/tier or endpoint, and live policy updates, are future
options if needed or more time is available. A real API with operations of different
costs would likely benefit from endpoint-specific policies. Such additions require
deciding quota keys, policy lookup, and how existing balances change with the policy.

## Policy semantics

- Capacity B is a positive integer number of tokens; approved default: 10.
- Refill rate R is a positive integer number of tokens per second; default: 2.
- Capacity and refill rate are configurable at startup; the defaults are approved.
- Every protected request costs exactly one token.
- A new client starts with a full bucket.
- Capacity replenishes continuously, capped at B. Unused time at capacity does
  not create extra credit beyond B.
- Before deciding, replenish using elapsed monotonic time since the previous
  calculation, then consume one token if available.
- Rejected requests do not consume tokens or discard fractional refill credit.
- A rejection reports the duration until one token could next be available,
  assuming no intervening successful request from that client.
- Accepted requests consume their token even if downstream handling fails.
- Limits are per client, shared across concurrent requests with the same identity.
- All protected business endpoints share that client's bucket under one policy;
  there is no separate allowance per route. The demo is the only business endpoint
  initially. Health is exempt. Admission consumes one token with no refund.

Example for B=3 and R=2:

| Time since start | Action | Result | Tokens after action |
| --- | --- | --- | --- |
| 0 ms | First request | Allow | 2 |
| 0 ms | Second request | Allow | 1 |
| 0 ms | Third request | Allow | 0 |
| 0 ms | Fourth request | Reject; wait 500 ms | 0 |
| 250 ms | Another request | Reject; wait 250 ms | 0.5 |
| 500 ms | Another request | Allow | 0 |
| 10,000 ms | Another request | Allow | 2 |

The table uses fractional tokens to explain behavior. The implementation represents
this credit using integer arithmetic, with explicit rounding and overflow guards.

## Time and arithmetic

Integer accounting, monotonic time, and the validation bounds are approved.

Use a small injectable elapsed-time source backed by `System.nanoTime()` in
production. Tests supply controlled ticks and never wait for real refill periods.
Calendar time and timezone do not affect quotas.

Supported ranges: B and R each 1 through 1,000,000; registry size 1 through
1,000,000. Validate at startup. Default registry limit is 10,000.

Represent one token as 1,000,000,000 credit units in a `long`. Maximum credit is
B times that scale, at most 10^15. Each elapsed nanosecond adds R credit units.
Before multiplying elapsed time by R, compare it with the upward-rounded time to
full capacity. If sufficient, set credit to the cap; otherwise the product is
bounded by the missing credit. This avoids overflow on long idle intervals.

Consume the scale for admission. If credit is insufficient, retry nanoseconds are
ceil((scale - credit) / R); HTTP Retry-After rounds that upward to seconds. The
remainder stays in the balance; rejection never discards it. Set the accounting
reference to the sampled time after nonnegative elapsed-time accounting, including
at capacity, so excess idle time cannot be banked.

Use subtraction for monotonic tick differences within the supported interval of
less than 2^63 nanoseconds. A negative difference from a test/custom time source
contributes no refill and must not move the reference backward. Test this defense.

## State and concurrency

Per-client locking is approved. Each client entry holds its accounting state and
associated lock. Refill, check, and consumption for the same client must be atomic;
different clients must be able to execute their accounting independently. Read
elapsed time within the protected accounting operation. Release locks before
endpoint execution, logging, or other external work.

Use a registry `HashMap` guarded by one `ReentrantLock`. Each entry has a separate
`ReentrantLock`, bucket state, and an in-use reference count guarded by the registry
lock. This count includes requests waiting for the bucket lock.

1. Acquire the registry lock. Look up the entry; for an unknown client, reclaim
   space if necessary, then create exactly one entry or report saturation. Increment
   the entry's reference count before releasing the registry lock.
2. Acquire that entry's lock, sample time, refill/check/consume, and release the
   entry lock in `finally`. Do not hold the registry lock during this accounting.
3. In an outer `finally`, acquire the registry lock, decrement the reference count,
   and release it. Release all limiter locks/references before endpoint execution.
4. At capacity, scan entries under the registry lock. Skip every referenced entry.
   For an unreferenced entry, use nonblocking `tryLock` on its bucket; skip on failure.
   Refill under that lock and remove it only if full, then release the bucket lock.
   Stop after reclaiming one slot. Perform creation before releasing the registry lock.

Requests never acquire the registry lock while holding a bucket lock. Cleanup never
waits for a bucket lock while holding the registry lock. Reference tracking prevents
removal while any active/waiting request can use the entry; the registry lock makes
creation, removal, and the capacity check atomic. Cleanup may safely refill a retained
unreferenced entry while checking it. Exceptional paths must release locks and pins.

Tests must establish a single authoritative bucket per identity, an exact registry
bound under concurrent creation, same-client serialization, cross-client independent
accounting, no detached-bucket consumption, and reference cleanup on failure. A
blocked accounting operation must not hold the registry lock. Reclamation scans
can temporarily delay registry access: this is a documented O(N) limitation, not a
claim of unlimited throughput or strict scheduling fairness.

The approved bound defaults to 10,000 entries and is configurable at startup. If no
unused full bucket can be reclaimed, return 503 to an unknown client; known clients
continue using their buckets. Never evict a depleted bucket. Capacity and rejection
are revisitable business decisions; there is no automatic growth or bypass. Entries
may stay resident below the bound; no maintenance thread is included.

## HTTP contract

| Request | Result |
| --- | --- |
| `GET /api/demo` with valid client and capacity | 200, JSON `{"message":"allowed"}` |
| Protected request with missing or invalid identity | 400, JSON `{"error":"invalid_client_id"}` |
| Protected request with exhausted quota | 429, JSON `{"error":"rate_limit_exceeded"}`, plus `Retry-After` |
| Unknown client when registry cannot admit an entry | 503, JSON `{"error":"client_capacity_exceeded"}` |
| Unexpected application failure | 500, JSON `{"error":"internal_error"}`; no stack trace exposed |
| `GET /health` | 200, JSON `{"status":"ok"}`; no identity required and no quota consumed |

Exactly one `X-Client-Id` value is required: 1-64 ASCII characters from letters,
digits, underscore, and hyphen; values are case-sensitive. Reject duplicate headers,
comma-separated values, whitespace within the parsed value, and other characters.
Header names follow HTTP case-insensitive matching. Validate before creating state
or consuming quota. Standard transport parsing may remove surrounding HTTP optional
whitespace; validate the value exposed by the servlet container. This header carries
an identifier, not a credential.

`Retry-After` is a positive integer number of seconds, rounded upward from the
computed delay. It is guidance, not a reservation of future capacity. No custom
quota headers are included. Demo and limiter error responses use JSON
content type and `Cache-Control: no-store`.

Use Spring MVC `HandlerInterceptor` admission for resolved business controller
methods under `/api/**`. Health and the framework error handler are outside that
business scope. Only GET invokes the demo operation; reject HEAD for the demo with
405 before admission/controller execution, since Spring otherwise supports HEAD
through GET mappings. OPTIONS returns metadata only (Allow: GET, OPTIONS); other
unsupported methods return 405, and unknown routes return 404, without consuming
quota. These routing responses need not share the limiter's JSON error contract.

The interceptor checks identity, calls the core once, and either proceeds or writes
the specified response. Process only the initial REQUEST dispatch; do not charge
again on error dispatch. The demo uses synchronous controllers, with no async or
forward-based business flow. A controller advice maps unexpected application
exceptions to the generic 500 and logs the error. Container-level malformed HTTP
is handled by the container. HTTP tests must verify the actual server behavior,
including HEAD/OPTIONS and that rejected requests never execute the demo operation.

[Spring's documented HEAD/OPTIONS behavior](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
motivates the explicit method guard.

## Configuration and error handling

Configuration properties: `limiter.capacity`, `limiter.refill-tokens-per-second`,
`limiter.max-clients`, and `server.port` (default 8080). Environment equivalents are `LIMITER_CAPACITY`,
`LIMITER_REFILLTOKENSPERSECOND`, `LIMITER_MAXCLIENTS`, and `SERVER_PORT`. Bind a
validated configuration properties object at startup. Invalid, zero, negative, noninteger,
or out-of-range policy values fail startup with an actionable message.

Core results distinguish allowance, quota rejection, and registry saturation.
HTTP code translates those results at the boundary. Unexpected errors are server
errors and must not silently allow requests. Do not expose stack traces to clients.
Log startup configuration and unexpected failures; avoid per-request logs and
client-ID logging by default.

## Selected tooling and project layout

Selected under the owner's delegation; verified against official documentation on
2026-11-01. Java versions were resolved and built successfully; Docker digests are pinned and image build/runtime execution passed. See VERIFICATION.md for the actual verification scope.

| Component | Selected version / source |
| --- | --- |
| Local Java distribution | Eclipse Temurin JDK 21.0.13+9; compile release 21 ([release](https://github.com/adoptium/temurin21-binaries/releases)) |
| Spring Boot parent and MVC starter | 4.1.2; embedded Tomcat, MVC and validation starters ([requirements](https://docs.spring.io/spring-boot/system-requirements.html)) |
| Test framework | JUnit Jupiter 6.0.3, managed by the Boot 4.1.2 dependency BOM; MVC test starter ([managed versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)) |
| Maven distribution | 3.10.0, selected stable release; wrapper pins distribution and checksum ([download](https://maven.apache.org/download.cgi)) |
| Coverage plugin | JaCoCo 0.8.16 ([official releases](https://www.jacoco.org/jacoco/index.html)) |
| Docker builder | `eclipse-temurin:21.0.13_9-jdk-noble` |
| Docker runtime | `eclipse-temurin:21.0.13_9-jre-noble` |

[Official container metadata](https://raw.githubusercontent.com/docker-library/official-images/master/library/eclipse-temurin)
lists both selected image tags. Their immutable image digests
were resolved from the registry and recorded in Dockerfile; version tags alone
can receive base-OS rebuilds. Record actual downloaded checksums rather than inventing
values. Use the Boot parent for managed compiler and Surefire/Failsafe plugin versions;
capture the effective POM during verification. Wrapper generation uses an official
wrapper release 3.3.4; generated metadata and distribution checksum are in `.mvn/wrapper`.

Boot 4.1.2 supports Java 21 and the selected Maven baseline. Using its managed
JUnit version avoids independently mixing test-framework versions. Temurin supplies
matching local and container runtimes. Ubuntu Noble images favor familiar glibc
compatibility over the smaller footprint of Alpine. Maven and JaCoCo release pins
avoid previews and snapshots. Compatibility, dependency resolution, and the Java build are verified. Container
execution has also been verified on the installed Colima engine.

One Maven module under `rate-limiter`, coordinates `com.iol.challenge:rate-limiter`,
version `0.1.0-SNAPSHOT`, base package `com.iol.challenge.ratelimiter`. Use standard
`src/main/java`, `src/main/resources`, and `src/test/java` layout. Separate `core`,
`web`, and `config` packages; the core has no Spring or HTTP dependencies. No database,
Redis, Lombok, or third-party limiter implementation is needed.

Use Boot's default logging backend: startup/policy at INFO, unexpected failures at
ERROR, console output. Do not log every rejected request or client identities by
default. No custom logging infrastructure or metrics backend is included.

## Packaging, verification, and environment

Multi-stage Docker build: the selected JDK image installs unzip (required to preserve
the pinned wrapper ZIP download/checksum) and runs the Maven Wrapper's `clean
verify` command, then the JRE image receives only the runnable JAR. Use a dedicated
non-root numeric user, port 8080, and exec-form Java startup. Publish the port explicitly;
use environment variables for policy settings. Keep local tool caches, Git metadata,
and build outputs out of the Docker build context. Use a build-stage report export
path when coverage reports are needed from a container-only build.

Commands from `rate-limiter` (Java and Docker commands verified):

- `./mvnw clean verify`: unit tests, integration tests, merged coverage, packaged JAR.
- `./mvnw spring-boot:run`: development run.
- `java -jar target/rate-limiter-0.1.0-SNAPSHOT.jar`: packaged run.
- `docker build -t iol-rate-limiter:local .`: tested image build.
- `docker run --rm -p 8080:8080 iol-rate-limiter:local`: container run.

JUnit `*Test` classes run under Surefire; `*IT` classes run under Failsafe through
`verify`. JaCoCo instruments both runs and merges their execution data before
reporting HTML/XML line and branch coverage for all application classes, including
the core. Target `target/site/jacoco/index.html`. Preserve meaningful assertions,
review uncovered paths, and document justified gaps toward the near-100% goal.
No arbitrary exclusions or enforced percentage threshold. Container behavior is
validated separately with real requests and shutdown checks; no Docker dependency
is imposed on the Java test suite.

The initial environment lacked Java and Docker tooling. Checksum-verified local
Java/Maven archives were acquired into ignored workspace directories. The owner
subsequently explicitly authorized an agent to install Docker and necessary host
prerequisites. The resulting local Colima/Docker setup and start/stop commands are
in [LOCAL-ENVIRONMENT.md](LOCAL-ENVIRONMENT.md). The application image, its full
Linux test suite, non-root runtime, HTTP behavior, shutdown, and coverage export
have now been verified. Downloads require network access.

Metrics, CI, and numerical threshold selection remain deferred to delivery review.
The challenge lists metrics as a nice-to-have and does not require CI or a percentage.

## Supplemental visible scenarios

At the owner's subsequent request, `scripts/probar_api.py` provides a standalone
black-box demonstration of the approved HTTP behavior using temporary containers.
It emits console output and HTML/JSON reports for review. It does not change the
application contract or replace Java tests; measured execution is recorded in
[VERIFICATION.md](VERIFICATION.md).

## Acceptance criteria

1. A clean checkout builds and tests using documented wrapper commands with the
   selected JDK; prerequisite downloads and network needs are documented.
2. A documented command starts the application; curl examples show 200, 400,
   429 with Retry-After, and independent client quotas.
3. Algorithm tests prove initial burst, refill boundaries, fractional retention,
   capacity cap, rejection behavior, and time/arithmetic edge cases.
4. Concurrent tests with frozen time prove exactly B successes among more than B
   simultaneous requests for a fresh client; no lost consumption is possible.
   Separate tests demonstrate independent progress across different clients.
5. State tests prove the registry bound, safe reclamation, saturation response,
   and preservation of quotas for active clients.
6. HTTP tests prove routing, identity validation, status/body/header mapping,
   configuration failure, and that health checks do not consume quota.
7. README and DESIGN.md match the implementation and explain all selected tradeoffs,
   limitations, AI use, and any code the owner still does not understand.
8. The owner reviews the completed behavior and approves submission readiness.
9. Docker packaging builds and the documented container demonstration works.
10. A coverage report records measured results and reviewed gaps against the goal
    of approaching 100%; passing tests remain mandatory regardless of percentage.

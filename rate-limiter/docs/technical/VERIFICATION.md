# Verification record

Date: 2026-11-01. Java and container verification pass. Docker engine remains running.

## Automated checks

Command from the project directory, with Java 21 configured:

```sh
./mvnw clean verify help:effective-pom -Doutput=target/effective-pom.xml
```

On this workstation, isolated `JAVA_HOME`, `MAVEN_USER_HOME`, and
`-Dmaven.repo.local` settings kept downloads outside global installations. Tool
archives were checked against their official checksums. The wrapper distribution
has a SHA-256 pin in `.mvn/wrapper/maven-wrapper.properties`.

- Build: SUCCESS, executable JAR produced.
- Unit tests: 16 passed, no failures/errors/skips.
- Real-server integration tests: 1 passed, no failures/errors/skips. This scenario
  verifies multiple HTTP/error/retry/identity cases against a controlled clock.
- Application line coverage: 114/116 = **98.28%**.
- Application branch coverage: 46/47 = **97.87%**.
- Core line coverage: 72/72 = **100%**.
- Core branch coverage: 29/30 = **96.67%**.

JaCoCo 0.8.16 combines unit and integration execution. HTML/XML reports are generated
under `target/site/jacoco`; the effective POM is under `target/effective-pom.xml`.
There are no application-code exclusions and no enforced numerical threshold.

The two uncovered lines are the application's `main` invocation, exercised separately
by the packaged-JAR smoke check but not instrumented in the Maven test processes.
The uncovered branch is the defensive failed `tryLock` during cleanup. Valid public
API use cannot hold a bucket lock with zero references, so forcing this branch would
require violating the internal protocol. It remains unforced and unexcluded.

## Behavioral evidence

- [TokenBucketTest](../../src/test/java/com/iol/challenge/ratelimiter/core/TokenBucketTest.java):
  burst, fractional retention, exact refill boundary, retry ceiling, capped/huge idle
  intervals, backward custom time, and monotonic counter wrap.
- [RateLimiterTest](../../src/test/java/com/iol/challenge/ratelimiter/core/RateLimiterTest.java):
  input bounds, identity isolation, saturation, safe reclamation, 64 simultaneous
  same-client requests, bounded concurrent creation, cross-client progress while one
  accounting call is paused, active-entry protection, failure cleanup, and concurrent
  reclamation without duplicate allowances. Synchronization uses bounded latches/futures.
- [LimiterPropertiesTest](../../src/test/java/com/iol/challenge/ratelimiter/config/LimiterPropertiesTest.java):
  binding plus zero, negative, excessive, fractional, and nonnumeric values for every setting.
- [ApplicationIT](../../src/test/java/com/iol/challenge/ratelimiter/ApplicationIT.java):
  health exemption; missing/invalid/duplicate IDs and length limits; client case sensitivity;
  404/405/HEAD/OPTIONS; 200/400/429/503/500 contracts; cache/content headers and retry
  guidance; exact refill; reclamation; downstream failure still spending its token;
  and recovery after a clock failure. Failure routes exist only in test sources.
- [AdmissionInterceptorTest](../../src/test/java/com/iol/challenge/ratelimiter/web/AdmissionInterceptorTest.java):
  error dispatch and non-controller handlers do not charge quota.

## Packaged application smoke check

The actual packaged JAR was started on an available local port with capacity 1,
refill 1 token/second, and one tracked client. Real HTTP requests verified health,
invalid identity, method guards, unknown routing, successful admission, 429 with
Retry-After 1, and 503 for another client while saturated. SIGTERM completed shutdown
(exit 143, expected for the Java process on this system). A separate launch with
capacity 0 exited 1 and reported the invalid property/value and validation reason.
Validation messages follow the system locale; the check uses the property identity.

## Docker installation and application verification

The owner authorized Docker installation and required supporting tools. The local
Colima engine, Docker CLI, Buildx, and Compose are available; see
[LOCAL-ENVIRONMENT.md](LOCAL-ENVIRONMENT.md) for versions and lifecycle commands.
The `colima` context is active, and `docker run --rm hello-world` passed.

These commands completed successfully against that engine:

```sh
docker --context colima build --progress=plain -t iol-rate-limiter:local rate-limiter
docker --context colima build --target reports --output type=local,dest=rate-limiter/target/container-coverage rate-limiter
```

The first container build detected a missing builder prerequisite: without unzip,
Maven Wrapper selected a tar archive incompatible with the pinned ZIP checksum.
Adding unzip to the builder fixed this; checksum validation remains enabled.
Builder and runtime base images remain pinned to official registry digests.

- Full Linux image build: SUCCESS; all 17 unit/integration tests pass inside it.
- Exported Linux report: 114/116 covered lines (98.28%), 46/47 branches (97.87%),
  matching the native Java verification.
- Image `iol-rate-limiter:local` is present in the Colima engine. Verified image
  manifest: `sha256:d0f39da846e1ba90df8a86dfe8a3861536122455e4cda39b45c16bbb30a29cbd`.
- Ran the image with `LIMITER_CAPACITY=1`, `LIMITER_REFILLTOKENSPERSECOND=1`, and
  `LIMITER_MAXCLIENTS=1`, publishing an ephemeral port on host loopback.
- Health 200, invalid identity 400, allowed request 200, exhausted quota 429 with
  Retry-After 1, and new-client saturation 503 all passed.
- Runtime `id` confirms UID/GID 10001. `/app/app.jar` exists and `/workspace` is absent.
- `docker stop --time 15` completed; logs confirm graceful shutdown. Exit 143 is
  expected for SIGTERM. No OOM termination occurred.
- Coverage export succeeded and contains `index.html`, XML, and associated report files.

The temporary smoke container was removed; the built image and Docker engine remain
available. No Docker application container is left running. No engine setup or
container verification blocker remains.

## Visible Python scenario runner

Owner requested an executable Python script to observe API cases directly, without
an additional checkpoint. [probar_api.py](../../scripts/probar_api.py) uses Python's
standard library and Docker. Executed successfully with:

```sh
python3 rate-limiter/scripts/probar_api.py --docker-context colima
```

Result: **26/26 cases passed**, exit 0. Both temporary containers were removed.
The runner checks valid/missing/empty/duplicate/malformed/overlength IDs; 64-character
identity; health; 404/POST/HEAD/OPTIONS behavior; preservation of quota for unmetered
requests; burst/exhaustion; independent/case-sensitive clients; Retry-After recovery;
20 concurrent same-client requests (exactly 3 admitted, 17 rejected before refill);
cap after idle time; invalid IDs not allocating tracking slots; 503 for new clients;
continued known-client admission; and safe reclamation after full replenishment.

Outputs: `target/casuisticas/resultados.html` and `resultados.json`, with expected
versus actual outcomes and elapsed time for each case. Rerunning creates fresh
containers and state. Existing application instances and containers are not touched.
Python scenarios are separate from the 17 Java tests and the JaCoCo coverage counts.
They use real time and explicitly reject ambiguous batch timings of one second or
longer. They do not establish benchmark throughput or replace deterministic core tests.

Metrics, CI, and coverage thresholds remain deferred. Public repository publication
and initial synchronization completed at [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL). Owner understanding
and submission-readiness review are not marked complete.

## Documentation consistency review

Reviewed README, business requirements, decision register, implementation plan,
specification, DESIGN.md, actual Java/Python sources, Dockerfile, and test evidence.
Corrected stale approval/build/publication statements and future-tense descriptions
of implemented behavior. Removed the source brief's machine-specific absolute path
from the public entry point. Architecture, admission, and per-client concurrency
diagrams in the root README describe the implemented locks/reference lifecycle.
All three Mermaid blocks were rendered successfully to SVG with Mermaid CLI using
a local browser. The Python script is explicitly the brief end-to-end demonstration. Generated
reports, downloaded tools, caches, and local logs remain ignored by Git.
Understanding and final submission review are not claimed complete; the owner
will answer five questions on the key algorithm/concurrency tradeoffs.

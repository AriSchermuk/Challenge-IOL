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
- Application line coverage: 123/125 = **98.40%**.
- Application branch coverage: 48/49 = **97.96%**.
- Core line coverage: 77/77 = **100%**.
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

The local
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
- Exported Linux report: 123/125 covered lines (98.40%), 48/49 branches (97.96%),
  matching the native Java verification.
- Image `iol-rate-limiter:local` is present in the Colima engine. Verified image
  manifest: `sha256:31200a11386181ce987c18fedc3cdc93b9262986bb20270e4862f36528a08682`.
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

[probar_api.py](../../scripts/probar_api.py) demonstrates API cases using Python's
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
and initial synchronization completed at [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL).

## Documentation consistency review

Reviewed README, business requirements, decision register, implementation plan,
specification, DESIGN.md, actual Java/Python sources, Dockerfile, and test evidence.
Checked build/publication statements and descriptions of implemented behavior. Removed the source brief's machine-specific absolute path
from the public entry point. Architecture, admission, and per-client concurrency
diagrams in the root README describe the implemented locks/reference lifecycle.
All three Mermaid blocks were rendered successfully to SVG with Mermaid CLI using
a local browser. The Python script is explicitly the brief end-to-end demonstration. Generated
reports, downloaded tools, caches, and local logs remain ignored by Git.

## Readability refactor verification

Selective Optional return types, descriptive unit/state names, constant-first String
equality, and `unpin`/`readClientId`/`writeDecision` extraction were applied.
Java `clean verify` passes (17 tests), Docker build passes with those same tests
inside Linux, the Python runner passes 26/26 scenarios, and container runtime smoke
checks plus coverage export pass. Current coverage figures above include the refactor.
Behavioral tests were preserved; only String equality orientation and descriptive
Stream lambda names changed in a test. Lock ordering, reference lifecycle, policy,
HTTP statuses, and configuration remain unchanged.

A clean export of the staged Git tree (without `.tools`, build outputs, or untracked
files) also built successfully as `iol-rate-limiter:clean-checkout`; its Linux build
reran all 17 tests successfully. This confirms the Docker build does not depend
on the workstation's ignored toolchain/cache directories.

## Fresh public GitHub clone verification

The public repository was cloned into a new temporary directory using `git clone`. Tested
commit: `afa5fc0996778b56eb1ddcdaf56d52139d786b8f`. The new worktree was clean and
contained neither `.tools` nor `target`.

`docker --context colima build --no-cache --progress=plain -t
iol-rate-limiter:github-clean-clone <clone>/rate-limiter` passed, downloading
dependencies and compiling inside the build image without Docker layer-cache reuse.
All 17 Java unit/integration tests passed, with no failures/errors/skips.

The Python script from that downloaded clone was then run against the resulting
`iol-rate-limiter:github-clean-clone` image: 26/26 cases passed, exit 0. Reports were
written to `target/casuisticas-clean-clone` in the original working directory.
Temporary scenario containers were removed normally. This verification covers
the public-clone Docker path, not a native Windows or Linux workstation install.
The subsequent commit only records this evidence; it does not change application code.

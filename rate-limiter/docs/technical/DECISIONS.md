# Decision register

Status: all six final technical choices approved by the owner. Delegated selections
are recorded in SPEC.md. The owner approved the consolidated specification and Phase 1 with “yep go ahead”.
The owner subsequently authorized continuation without phase checkpoints when
work follows the approved plan. Java and Docker implementation/verification are complete. The owner explicitly
authorized Docker host setup, superseding the earlier restriction on global
installation changes for that setup only.

## Confirmed

| ID | Decision | Evidence |
| --- | --- | --- |
| C1 | Use Java | Owner explicitly selected Java. |
| C2 | Write and review a complete specification before coding | Owner requested this workflow. |
| C3 | Owner decides at meaningful crossroads | Explicit collaboration agreement. |
| C4 | Separate business and technical documentation; keep specification and implementation aligned | Owner explicitly requested this organization and ongoing alignment. |

## First decisions: scope

| ID | Question | Recommendation | Alternative and consequence | Status |
| --- | --- | --- | --- | --- |
| D1 | What deployment scope? | One Java process, in-memory state | Owner selected single-process scope and requested a documented future path to multiple instances, including its benefits. No shared-state technology is selected. | Approved |
| D2 | Which algorithm? | Token bucket | Owner selected token bucket and requested a documented future direction for configurable algorithm selection. Integer refill accounting and bounds were approved in the final batch. | Approved |
| D3 | How is it used? | Limiter embedded in a demo HTTP API, with core logic independent of HTTP | Owner explicitly selected the embedded demo API after reviewing all three options. Demonstrates enforcement in one runnable application without a remote decision call. | Approved |

## Implementation decisions

The owner explicitly selected the following choices. The owner subsequently approved all six final technical recommendations, including
the detailed HTTP contract, arithmetic, safe reclamation, coverage scope, and tooling
delegation. The consolidated specification was approved before implementation.
For the D3 comparison, see [usage options](../business/USAGE-OPTIONS.md).

| ID | Decision | Rationale / consequence | Status |
| --- | --- | --- | --- |
| D4 | Java 21 and Spring Boot MVC | Owner selected both. Delegated version selections and sources are recorded in SPEC.md. | Approved baseline/framework |
| D4a | JUnit | Owner selected the test framework; JUnit Jupiter version is managed by the selected Spring Boot release in SPEC.md. | Approved framework |
| D4b | Maven with Maven Wrapper | Owner delegated the build-tool choice. Assistant selected Maven; tradeoffs below. | Selected under explicit delegation |
| D5 | Required `X-Client-Id` header | In a real deployment, IDs should be pre-approved/validated by a trusted traffic layer. Exactly one 1-64 character ASCII letters/digits/underscore/hyphen value; case-sensitive. Invalid input returns 400 without consuming quota. No gateway/authentication implementation is implied. | Approved |
| D6 | Single policy, independent client buckets; charge on admission | Approved startup-configurable defaults: capacity 10, refill 2 tokens/second. Admitted requests spend a token regardless of downstream result. Different client/tier/endpoint policies and live changes are future options if needed or more time is available. | Approved behavior and defaults |
| D7 | Per-client locking | Owner wants demonstrable concurrency across clients. Owner approved brief registry coordination with active/waiting reference counts, per-client accounting locks, and removal only of unused full buckets. | Approved |
| D7a | Numeric/time accounting | Scaled integer credit, monotonic nanosecond time, upward retry rounding, and configuration bounds 1-1,000,000 approved in the final batch. No refill thread. | Approved |
| D8 | Hard limit of 10,000 tracked clients by default, configurable at startup; 503 for new clients when no space is available | Owner approved the defaults and saturation response. Existing clients keep their buckets. This is a revisitable business decision: supported usage/capacity may be extended later. Reclaim only full buckets with no active/waiting requests; approved in the final batch. | Approved business policy |
| D9 | Shared policy across business endpoints; demo endpoint enabled; health exempt | Return 429 with Retry-After and a simple error code for exhausted quota. Simple success response. Endpoint-specific policies are documented future work. Exact endpoint/status/body contract and unmetered non-business requests approved in the final batch; see SPEC.md. | Approved behavior |
| D10 | Logging and Docker packaging | Owner selected both; multi-stage build runs tests; runtime uses a non-root user. Document and verify local/container execution. Metrics deferred. | Approved scope |
| D11 | Coverage report; aim as close to 100% as practical | Line and branch reporting across application code, combining unit/integration execution, without exclusions merely to improve percentages. No enforced threshold selected. CI, metrics, and threshold decisions deferred to delivery review. | Report/aspiration approved; extras deferred |

## Final-batch approval and delegation

The owner's “all decisions are good” approves the six final recommendations:
integer accounting/ranges; per-client coordination and safe cleanup; exact identity
syntax; HTTP contract; application line/branch coverage; and delegated routine setup.

The assistant may select compatible stable releases, a maintained Java 21 distribution,
container images, standard project structure, logging, commands, and isolated tooling.
No new product features, global installations, or changes to approved behavior are
included in that delegation. SPEC.md records the selected releases and implementation
protocol. Image digests and effective build metadata were captured during verified Java
and container builds; see VERIFICATION.md.

The consolidated specification and implementation start were approved. Continued
work within the plan is authorized without routine milestone approvals.
Metrics, CI, and a numerical coverage threshold remain deferred. Public repository
publication is now authorized; authentication is in progress.

## Business ownership of capacity

The approved registry size and 503 admission response represent the current business
policy, not an intrinsic requirement of token bucket. Increasing supported usage
(for example, raising the configured client bound) may be selected later after
reviewing resource use and operating requirements. Changes require an explicit
business decision and corresponding specification/tests; the current prototype
does not automatically grow past its bound or bypass limiting at saturation.

## Challenge requirement check

The brief requires working code, passing tests, and comprehensive core-logic tests.
It lists logging, metrics, and ease of deployment as nice-to-haves. It does not
require CI, a coverage report, or a numerical coverage threshold. Docker and the
coverage report are owner-selected deliverables. Revisit deferred extras at delivery;
none becomes mandatory merely because it was mentioned in the brief as a nice-to-have.

## Build-tool rationale

### Maven versus Gradle (D4b, selected: Maven)

Both can build this project and run JUnit. The owner delegated this choice to the
assistant, who selected Maven with Maven Wrapper. Its standard lifecycle fits the
proposed conventional Java service without custom build orchestration. This is a
project-specific preference, not a claim that Gradle is unsuitable.

Accepted tradeoffs: more verbose XML and less convenient custom task composition.
Gradle's up-to-date checking and optional task-output caching could help a larger
or more repetitive build workload. This project has no measured performance need
that justifies choosing around those advantages. Both tools support wrappers and
can support controlled builds. Maven selection does not select a Java version or
HTTP framework.

| Aspect | Maven | Gradle |
| --- | --- | --- |
| Configuration | XML POM with dependencies and plugin configuration | Kotlin or Groovy build scripts with plugins, dependencies, and tasks |
| Execution model | Standard ordered lifecycle phases; plugins bind work to phases | Task dependency graph; plugins provide conventional Java tasks |
| Custom work | Configure or add plugins; unusual workflows can be awkward | Flexible task definitions; custom logic needs maintenance and review |
| Repeated builds | Adequate baseline for this small project; actual timings need measurement | Up-to-date checks and optional build caching can avoid repeated work; gains depend on workload |
| Version consistency | Maven Wrapper pins/downloads the selected Maven distribution | Gradle Wrapper pins/downloads the selected Gradle distribution |

Neither wrapper alone makes every build input reproducible. Both still require
controlled dependencies, plugins, and JDK configuration. No performance comparison
has been measured for this project. Familiarity should carry more weight than
potential optimization benefits for this small service. With the choice delegated,
the standard lifecycle is the deciding factor.

Sources: [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html),
[Maven Wrapper](https://maven.apache.org/wrapper/),
[Gradle build scripts](https://docs.gradle.org/current/userguide/writing_build_scripts.html),
[Gradle build caching](https://docs.gradle.org/current/userguide/build_cache.html),
[Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

## Approval gate

All requested individual decisions are settled or explicitly deferred. The consolidated SPEC.md and implementation start were approved. Continue to request decisions at any new meaningful crossroads.

Record decisions without inferring approval from silence. Approved design choices
are not claims of implemented functionality.

## Phase 1 execution record

Local repository initialized. Spring Boot skeleton, validated settings, demo/health
routes, Maven Wrapper 3.3.4 and an HTTP smoke test implemented. Maven verification
passed through the wrapper. Isolated Temurin/Maven archives were checksum-verified.
No global installations, remote publication, limiter core, Docker, or coverage yet.

## Continuous execution and delivery status

The owner explicitly waived routine phase approval stops if work stays within the
approved plan. Core, per-client registry, HTTP integration, tests, coverage, Docker
packaging, and docs are implemented. Verification results and coverage gaps are in
[VERIFICATION.md](VERIFICATION.md). Docker image build, runtime behavior, shutdown,
and coverage export are now verified on the installed Colima engine. Metrics/CI/threshold remain deferred; public publication is now owner-authorized.

## D12: local Docker setup (explicitly authorized)

The owner requested an agent make Docker and its prerequisites fully available.
An agent installed official prebuilt Docker CLI, Colima/Lima, Buildx, and Compose;
created a 2-CPU/4-GiB/30-GiB VM using macOS virtualization; and verified hello-world.
The parent verified the actual application build/runtime and coverage export.
Setup details are in [LOCAL-ENVIRONMENT.md](LOCAL-ENVIRONMENT.md). No login-startup
service or system Docker socket symlink was added. This environmental setup does
not change the approved application behavior or deferred metrics/CI/threshold scope.

## D13: visible Python scenarios (explicitly requested)

The owner requested creating and executing a Python scenario runner without a
checkpoint. Implemented `scripts/probar_api.py`: standard-library HTTP/Docker runner,
console outcomes, HTML/JSON report, temporary isolated containers, and no additional
application behavior. It passed 26 cases; verification evidence is recorded in
VERIFICATION.md. No new Python dependency or application feature was introduced.

## D14: diagrams, documentation review, and public GitHub repository

The owner requested architecture/flow/concurrency diagrams in the README, five
understanding questions, a documentation-consistency review, and synchronization
to a public repository in their own GitHub account. This authorizes repository
creation and push without an additional publication checkpoint. Repository name
will follow the workspace: `Challenge-IOL`; existing unrelated repositories must
not be overwritten. GitHub authentication is required to finish publication.

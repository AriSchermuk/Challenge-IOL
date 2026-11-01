# Implementation and review plan

Status: Java implementation, automated verification, coverage, and packaged-JAR
checks complete. Docker image build/runtime and report export also pass. The owner
authorizes work within the plan without routine phase approval stops.

Follow the [documentation alignment rules](../README.md). At each phase checkpoint,
update the requirement mapping in the [technical specification](../technical/SPEC.md)
with actual implementation/test links and any remaining gaps. Review business
requirements, technical contracts, code, and tests together when behavior changes.

## Phase 0: settle and approve the specification

- Completed: scope, framework, accounting, identity/HTTP contracts, concurrency,
  safe reclamation, numeric defaults, capacity behavior, Docker, and coverage decisions.
- Completed: delegated tool release selection and official compatibility checks;
  record sources in SPEC.md. Java and container builds/downloads were subsequently completed and verified.
- Completed: read-only environment inventory; isolated tooling is authorized after
  implementation approval. Docker was subsequently installed under explicit owner authorization and verified.
- Completed: align SPEC.md, DECISIONS.md, and business requirements.
- Completed: owner approved the consolidated specification and Phase 1.

Milestone: owner approves the complete specification and implementation start.

## Phase 1: runnable skeleton

Implemented. Wrapper verification passed; one real HTTP smoke test passed with no
failures or skips. See [project README](../../README.md) for runnable instructions. Complete; no phase approval pause required.

- Initialize the local repository and `rate-limiter` project after implementation
  approval and setup review. Public GitHub hosting is complete: [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL).
- Add the selected build wrapper, dependencies, and minimal application entry point.
- Add health and demo routes and minimal configuration validation.
- Establish the documented build/run commands and a smoke test.

Evidence: project builds, tests run, application starts, health endpoint responds.
Milestone: review project structure, dependency purposes, and request flow.

## Phase 2: token bucket core

Completed and verified; see the verification record.

- Implement the policy, time source, bucket accounting, and decision representation.
- Keep HTTP/framework types out of the algorithm.
- Implement refill, consumption, retry calculation, and arithmetic bounds.
- Add deterministic tests using controlled time.

Required cases: full initial bucket; exhaustion; just before/at/after replenishment;
repeated partial refills; long idle time; rejection retaining credit; high supported
values; invalid values; clock anomaly defense; no credit accumulating above capacity.

Evidence: focused core tests pass and explain expected behavior independently of
internal fields or helper-method structure.
Milestone: owner walks through a bucket example and reviews arithmetic/concepts.

## Phase 3: client registry and concurrency

Completed and verified; see registry/concurrency tests in the verification record.

- Add independent client state and per-client locking, following the lifecycle
  reference-counting and reclamation protocol in SPEC.md.
- Implement the approved bound and saturation response. Use the approved safe reclamation
  protocol and preserve active/waiting client allowances.
- Exercise concurrent requests through the same public core API used by HTTP.

Required cases: clients remain independent; N simultaneous attempts against capacity
B yield exactly B successes with frozen time; concurrent first access creates one
logical bucket; mixed client requests preserve each quota; registry never exceeds
its bound; depleted entries are not reclaimed; full entries can be safely replaced;
known clients continue working during saturation.

Demonstrate that holding one existing client's accounting operation does not block
another existing client's accounting. Verify same-client serialization, safe
concurrent first use, and no stale-bucket use during removal/recreation. Use the
approved lock order in tests for registry lifecycle races; a registry lock must not
silently serialize all clients' accounting. Assertions should exercise the real
coordination path with controlled scheduling, not rely on throughput timings.

Use barriers/latches and bounded completion waits, not sleeps, to coordinate tests.
Include concurrent admission/reclamation coverage. Surface task failures and always
shut down test executors. Do not assert scheduler-dependent ordering.

Evidence: deterministic correctness tests pass. No throughput claim is made without
an agreed benchmark and environment.
Milestone: owner understands the race prevented by locking and eviction tradeoffs.

## Phase 4: HTTP integration

Completed and verified with a real embedded server and packaged-JAR smoke checks.

- Validate identity before state access.
- Document the trusted traffic layer requirement for pre-approved IDs; do not
  imply that header syntax validation implements that external trust boundary.
- Connect the core to the selected request interception mechanism.
- Map decisions to the approved HTTP contract and configuration.
- Verify the limiter scope, bypass rules, and error paths using a real embedded
  server where appropriate, with injected time for refill-sensitive tests.

Required cases: allowed/rejected requests, retry rounding, JSON and cache headers,
invalid/missing/duplicate identities, case-sensitive client values, independent
clients, registry saturation, health bypass, unsupported methods, unknown paths,
invalid startup configuration, generic application errors, and HEAD/OPTIONS bypass
without business execution. Verify each admitted business request is charged once.

Evidence: HTTP integration tests and documented curl demonstrations work.
Milestone: owner reviews externally visible behavior and configuration.

## Phase 5: delivery documentation and verification

Documentation, measured coverage, and Java verification complete. Docker execution and coverage export also pass. Owner understanding review and
submission readiness remain. Deferred extras have
not been silently added. See [verification record](../technical/VERIFICATION.md).

- Write README prerequisites, build/test/run steps, configuration, examples, and limits.
- Write DESIGN.md covering architecture, algorithm, concurrency, memory lifecycle,
  alternatives rejected, error behavior, and how AI was used and reviewed.
- Include the owner's requested future directions: coordinated limits across
  multiple instances, configurable algorithm selection, and alternate client/tier/
  endpoint policies or live updates. Explain benefits, prerequisites, and limitations
  without presenting them as implemented features.
- Add Docker packaging, verify image build and actual container startup, health,
  admission/rejection, configuration, and shutdown; document the tested commands.
- Generate a coverage report, inspect line/branch gaps, and add meaningful tests
  toward the owner's goal of approaching 100%. Record remaining gaps and reasons;
  do not claim a measured percentage before a successful coverage run.
- Revisit deferred metrics, CI, and coverage threshold with the owner at delivery.
  None is mandatory under the brief; add them only if selected then.
- Review the code with the owner. Explain unclear pieces; clearly comment any that
  remain unclear, as required by the challenge.
- Run the complete build and test suite and execute the documented startup/demo path.
- Review changes for unnecessary abstractions, dependencies, generated boilerplate,
  redundant comments, and accidental secrets or local machine paths.

Evidence: recorded verification commands/results, current docs, and requirement coverage.
Milestone: owner approves submission readiness. Publish/share only as authorized.

## Rules for changes during implementation

New decisions about behavior, dependencies, concurrency strategy, scope, or tradeoffs
return to the owner before changing behavior. Describe the trigger, options, recommended
choice, and affected specification/tests. Update the decision register and spec after
approval. Do not silently add Redis, new algorithms, authentication, metrics services,
or a UI to make the solution appear more sophisticated.

## Testing approach

Use unit tests for the algorithm, concurrency tests for atomic behavior and state
lifecycle, and integration tests for the HTTP/configuration boundary. Mock external
boundaries only where useful; test the actual limiter, not a mocked replacement.
Test observable invariants and boundary cases rather than private method calls.
Tests for time behavior must be repeatable and fast. Run focused tests while editing,
then the complete verification command for delivery.

Include the selected JaCoCo coverage reporting in Maven verification. Report
actual measured coverage and test results; no numerical threshold is currently
approved. Tests must establish correct behavior even if coverage is high.

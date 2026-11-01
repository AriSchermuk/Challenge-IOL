# Implementation and verification map

This document maps the delivered components to their validation. Detailed outcomes,
coverage, and clean-clone evidence are in [VERIFICATION.md](../technical/VERIFICATION.md).

| Component | Delivered behavior | Validation |
| --- | --- | --- |
| Application and configuration | Java 21 MVC API, startup validation, Maven Wrapper | Native and container builds; invalid-property tests and packaged startup checks |
| Token bucket | Integer credit, continuous replenishment, capped balance, retry delay | Controlled-clock boundary, overflow, clock anomaly, and wrap tests |
| Client registry | Per-client locks, pinned references, bounded creation, safe reclamation | Same-client contention, cross-client progress, saturation, cleanup and failure tests |
| HTTP adapter | Identity validation, shared policy, method/health exemptions, error mapping | Embedded real-server tests and packaged/container smoke checks |
| Docker | Pinned builder/runtime images, tested build, non-root runtime | Full Linux test suite, HTTP requests, UID inspection, shutdown, report export |
| Python demonstration | Isolated temporary containers, expected/actual results, HTML/JSON | 26 passing black-box cases |
| Documentation | Requirements, specification, design, decisions, diagrams, commands | Link checks, Mermaid rendering, source/contract consistency review |
| Distribution | Public GitHub repository | Fresh clone; uncached Docker build and Python scenarios from that clone |

## Verification commands

From `rate-limiter`:

```sh
./mvnw clean verify
docker build -t iol-rate-limiter:local .
python3 scripts/probar_api.py
```

The Maven command runs unit/concurrency tests through Surefire, HTTP integration
tests through Failsafe, and combined JaCoCo reporting. It does not require Docker.
The Docker build runs the Java suite inside Linux. Python needs Docker and uses
real time; it does not contribute to JaCoCo coverage.

## Maintenance checks

- Keep requirements, technical contracts, implementation, tests, and documentation
  aligned when behavior changes. Preserve requirement IDs and evidence links.
- Cover arithmetic boundaries, independent client quotas, atomic same-client
  decisions, registry bounds, and removal/recreation races.
- Use controlled clocks and bounded latches/futures for Java concurrency tests.
  Do not infer correctness from scheduling order or throughput measurements.
- Check identity, routing, retry rounding, JSON/cache headers, failure behavior,
  startup configuration, and absence of double charging.
- Generate coverage and inspect uncovered paths; do not inflate percentages using
  arbitrary exclusions or assertions that merely mirror the implementation.
- Verify documented run commands and a clean build path. Keep tools, caches, logs,
  generated reports, and credentials outside version control.

CI, metrics, and a numerical coverage threshold are future operational additions.

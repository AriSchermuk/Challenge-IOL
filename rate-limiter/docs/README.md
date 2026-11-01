# Documentation guide

The documentation describes the implemented solution, its tradeoffs, and measured
verification. Start with the [project README](../README.md) for setup and commands.

| Document | Purpose |
| --- | --- |
| [Business requirements](business/REQUIREMENTS.md) | Purpose, outcomes, scope, and stable requirement IDs |
| [Usage options](business/USAGE-OPTIONS.md) | Integration alternatives and consequences |
| [Technical specification](technical/SPEC.md) | Exact behavior, configuration, constraints, and requirement traceability |
| [Technical decisions](technical/DECISIONS.md) | Selected approaches, alternatives, and tradeoffs |
| [Verification](technical/VERIFICATION.md) | Executed checks, coverage, and clean-clone evidence |
| [Local environment](technical/LOCAL-ENVIRONMENT.md) | Workstation-specific Docker tools and lifecycle commands |
| [Implementation map](delivery/PLAN.md) | Delivered components, validation, and maintenance checks |
| [DESIGN.md](../DESIGN.md) | Architecture, concurrency reasoning, limitations, and AI use |

Business documents explain outcomes in plain language. Technical documents explain
contracts and mechanisms. The implementation map connects delivery to validation.
Link to the document that defines a detail instead of repeating exact values across
several places.

Keep implemented behavior, future extensions, and verification gaps distinct.
Changes should update affected requirements, specification sections, code, tests,
and usage instructions together. Requirement traceability links to actual sources
and tests; implementation alone is not evidence that a behavior was verified.

Before release, check relevant tests, documented commands, Markdown links, diagrams,
and the requirement mapping. Documentation alignment is maintained through tests
and review; there is no automatic Markdown/code synchronization feature.

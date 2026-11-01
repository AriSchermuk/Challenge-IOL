# Documentation guide

Current state: approved implementation, Java verification, and Docker image/runtime
verification complete. Local Docker tooling is installed and the engine is running. Follow [verification evidence](technical/VERIFICATION.md).
The owner authorizes continuous work within the approved plan, without routine
phase-approval pauses. New meaningful deviations still require an owner decision.

## Organization and authority

| Document | Audience and purpose | Owns |
| --- | --- | --- |
| [Business requirements](business/REQUIREMENTS.md) | Owner and reviewers who need product context | Why the limiter exists, intended outcomes, scope, business requirement IDs |
| [Usage options](business/USAGE-OPTIONS.md) | Owner choosing how the limiter is used | Explanation of integration alternatives; not a record of approval |
| [Technical specification](technical/SPEC.md) | Implementer and technical reviewer | Exact behavioral contracts, constraints, and acceptance criteria |
| [Decision register](technical/DECISIONS.md) | Owner and implementer | Pending/approved decisions, alternatives, and rationale |
| [Local environment](technical/LOCAL-ENVIRONMENT.md) | Developer running the project here | Installed Docker/VM tools, context, lifecycle commands |
| [Delivery plan](delivery/PLAN.md) | Owner and implementer | Work sequence, review gates, and verification approach |
| [DESIGN.md](../DESIGN.md) | Challenge reviewer | Architecture actually implemented, tradeoffs, limitations, and AI use |

The repository README is the entry point. [The project README](../README.md)
documents setup, configuration, and usage commands. Keep `DESIGN.md` directly
inside the problem folder as required by the challenge.

Business documents use plain language and focus on user outcomes. Technical
documents explain how those outcomes are achieved and the decisions behind them.
Delivery documents describe how work is performed and checked. Link to the owner
of a detail rather than copy exact configuration values or API contracts into
several places.

## Alignment rules

1. Label statements as approved, proposed, future, or implemented as appropriate.
   Approval is permission to build a design; it is not evidence that it exists.
2. Give business requirements stable IDs. The technical specification maps those
   IDs to contracts and planned verification. Once code exists, extend that mapping
   with actual source/test links and verification status.
3. For a meaningful change, record the proposed decision and affected requirements,
   specification sections, implementation, tests, and docs. Obtain the owner's
   decision before implementing changed behavior.
4. Update the approved specification before coding, then deliver code, tests,
   design explanation, and user instructions together in the same change set.
5. At every checkpoint, compare implemented behavior against the approved spec.
   Record gaps explicitly; never silently rewrite the spec to excuse a bug, or
   label a requirement complete because code exists without verification.
6. Before completion, run relevant tests, verify documented commands, check links,
   and review the requirement mapping. Future features remain clearly separated.

There is no automatic synchronization between Markdown and code. Alignment is a
review requirement supported by tests and the mapping, not a claimed tool feature.

## Conflicts

The owner's latest explicit decision governs the work. If documents disagree,
reconcile them with that decision before continuing dependent implementation.
If the decision does not resolve the conflict, bring the choice to the owner.
Do not treat either existing code or a draft document as implicit approval.

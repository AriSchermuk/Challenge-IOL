# Business requirements

Status: scope and specification approved; implementation and verification complete.
Owner understanding and submission-readiness review are in progress.
The approved choices are recorded in the technical decision register.

## Purpose

Demonstrate a rate limiter that controls how quickly each client can use a service.
The intended benefit is to reduce excessive consumption by individual clients
while allowing short bursts of legitimate activity. Per-client limits alone do
not guarantee fairness, total service capacity, or protection against attackers
who can invent new identities.

For this challenge, success means a working, tested solution whose behavior and
tradeoffs the owner understands and can explain during an interview.

## People and outcomes

- A service operator chooses the permitted burst and sustained request rate.
- A client receives service while within its allowance and a clear response when
  it must slow down.
- A reviewer can run the demonstration, test its behavior, and understand its limits.

Clients identify themselves through `X-Client-Id`. In a real deployment, these
identities should be pre-approved/validated by a trusted traffic layer that prevents
impersonation. That layer is outside this prototype; accepting a well-formed header
in the demo does not authenticate or approve its sender.

## Requirements

| ID | Outcome | Status |
| --- | --- | --- |
| B1 | Deliver the initial limiter within one Java process. | Approved scope |
| B2 | Use a token bucket: allow a bounded burst and replenish the allowance over time. | Approved, including integer accounting and startup-configurable defaults |
| B3 | Track clients separately under one shared policy so one client does not spend another's allowance. | Approved |
| B4 | Return 429 with retry guidance and a simple error code for exhausted quota. | Approved; exact contract recorded in SPEC.md |
| B5 | Preserve allowance rules for simultaneous requests and allow different clients to make progress independently. | Approved; per-client locking selected |
| B6 | Impose a configurable hard limit on tracked clients; reject new clients with 503 when no space is available, preserving existing client quotas. | Defaults and saturation response approved; revisitable business policy |
| B7 | Provide a reproducible demonstration, automated tests, and understandable documentation. | Required |
| B8 | Demonstrate enforcement within a runnable API, checking requests locally before running the protected endpoint. | Approved integration scope |
| B9 | Include logging, Docker packaging, and a coverage report aiming as close to 100% as practical. | Approved; no enforced threshold |

An allowance is consumed on admission even if subsequent application work fails.
All business endpoints share the client's allowance; the demo endpoint is enabled
and returns a simple success response. Health checks consume no quota. Requests exceeding the allowance are rejected
immediately rather than queued.

## Initial scope and limitations

The initial release runs in one process and implements token bucket only. The
state is in memory: restarting resets allowances; running independent
copies gives clients an allowance at each copy. The limiter is embedded in
a demo API; see [usage options](USAGE-OPTIONS.md) for the selected option and alternatives.

Authentication, billing plans, a user interface, persistent quotas, and guaranteed
production capacity are outside the initial scope. Numeric defaults and exact
response contracts live in the [technical specification](../technical/SPEC.md).

## Future direction

Client capacity and the 503 response at saturation are business decisions that may
be revisited as demand grows. Increasing supported usage, such as raising the client
bound after checking resource needs, is a future option. The prototype continues
to enforce the configured hard limit until such a change is explicitly approved.

- Multiple instances sharing the same client allowance: spread traffic across
  application instances and support instance replacement without automatically
  resetting that allowance. Availability benefits depend on the whole design.
- Configurable algorithm selection: choose different burst, timing, precision,
  and resource-use characteristics for different workloads.
- Different policies by client/tier or endpoint and live configuration changes:
  implement if needed or more time is available. In a real API, different operations
  would likely benefit from their own endpoint policies.

The owner requested these directions as roadmap notes. They are not initial-release
deliverables. Technical prerequisites are in the specification.

Metrics, CI, and an enforced coverage threshold are deferred to the delivery review.
Metrics are optional in the challenge; CI and a numeric threshold are not required.

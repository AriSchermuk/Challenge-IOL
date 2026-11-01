# How the limiter could be used

The implementation uses option 1, an embedded demo API. Alternatives are retained
to explain integration tradeoffs. Examples are illustrative; SPEC.md defines the API.

All three options can implement the same token bucket rules. The choice determines
who calls the limiter, how it is demonstrated, and what must be deployed.

## Option 1: embedded in a demonstration API

A client calls a Java API. Inside that same process, the application asks the
limiter whether the client has an allowance available. If allowed, the endpoint
does its work; otherwise the application immediately returns a rejection.

Flow: client -> demo API -> local limiter decision -> endpoint or rejection.

We build the core limiter, a small API, the integration between them, and tests.
The reviewer starts one application and makes repeated HTTP requests to see success,
rejection, replenishment, and independent client limits.

Advantages: a tangible end-to-end demo, no extra network call for decisions, and
one application to operate. The core can remain independent of the HTTP framework.

Costs and limits: each independent application instance has its own state initially.
Another application must integrate the Java component or its equivalent. Extracting
a published reusable library would be additional packaging work, not automatic.

This is the selected option because it shows the core logic working in a
real request path with a small operational scope. Future multiple-instance support
could preserve this integration while coordinating state externally.

## Option 2: standalone decision service

A client calls a business application. That application asks a separate limiter
service for permission. An allowed response means the application may proceed;
a rejected response means it should decline the original client request.

Flow: client -> business application -> limiter service -> decision back to application.

The limiter does not automatically forward requests or enforce that the calling
application obeys the decision. Building a gateway/proxy would be another scope.

We build a decision API, the core limiter, and a caller/example showing how to use
it. The protected application owns enforcement of the returned decision. Different
programming languages can call the same service.

Advantages: centralize decisions for several callers and update the limiter without
redeploying every caller. A single limiter process can share its in-memory allowance
across those callers if client identities and quota scope are defined consistently.

Costs and limits: each check adds network latency and a dependency. We must decide
what callers do on timeout or outage. If permission was consumed but the response
was lost, retrying may consume another token; this requires an explicit retry policy
and possibly request deduplication. One decision process is also a capacity and
availability constraint. Replicating it still requires coordinated state.

This fits a project intended to explore service boundaries and failure handling,
but those concerns materially expand the specification and demonstration.

## Option 3: Java library with a command-line demonstration

A Java caller invokes the limiter directly and handles the returned result. A
command-line runner simulates requests and prints whether each was allowed.

Flow: Java caller -> local limiter method -> decision back to caller.

We build the public Java API, core limiter, usage example, and tests. No HTTP server
is required. The reviewer runs tests and a repeatable CLI scenario.

Advantages: smallest operational footprint, focused algorithm/concurrency review,
and a directly reusable Java component. Application teams choose their own response
handling and framework.

Costs and limits: the reviewer sees simulated calls rather than a protected HTTP
endpoint. Integrating it into a real service remains the caller's work. Public API,
packaging, and usage documentation become central deliverables. A local library
still has separate state in separate processes unless later extended.

This fits a focused component challenge if that presentation is preferred. An HTTP
adapter can be added later, but it would be an additional integration feature.

## Comparison

| Dimension | Embedded API | Decision service | Library + CLI |
| --- | --- | --- | --- |
| Reviewer experience | Call a protected endpoint | Observe a permission service and caller | Run tests and simulated calls |
| Decision call | Within process | Across network | Within process |
| Caller language | Java integration | Any language with HTTP | Java/JVM integration |
| Main extra work | HTTP interception and responses | Caller behavior, network failures, decision contract | Public Java API and packaging |
| Initial state | Local to demo application | Local to decision service | Local to each library instance/process |
| Enforces business request | Embedded integration | Calling application must enforce | Calling application must enforce |

Changing integration style later is possible, but each option introduces its own
contracts and tests. None automatically provides correct distributed quotas.

# IOL system design challenge

Status: approved implementation, Java tests, coverage, Docker image build, and
container runtime verification complete. Local Docker engine is installed and running. See the
[project README](rate-limiter/README.md) and [verification record](rate-limiter/docs/technical/VERIFICATION.md).

The project is a single-process Java 21 token bucket rate limiter embedded in a
Spring Boot MVC demo API. All individual scope and technical choices are approved;
the consolidated specification was approved before implementation.

JUnit, Maven Wrapper, per-client locking, validated client-ID headers, a shared
startup-configured policy, a hard client limit, logging, Docker, and line/branch
coverage reporting are included. Exact delegated tool selections and their sources
are recorded in SPEC.md. Metrics, CI, and a coverage threshold remain deferred. Public repository: [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL). Multiple-instance support, configurable algorithms, and alternate
client/endpoint policies are documented future directions.

## Run the demonstration

The Python script is the brief end-to-end demonstration: it creates isolated Docker
containers, prints expected/actual results, and produces HTML/JSON reports.

```sh
python3 rate-limiter/scripts/probar_api.py
```

Requires Python 3.9+ and a running Docker engine. If the image is absent, it is built
automatically. See [run instructions](rate-limiter/README.md) for Java/Docker commands,
configuration, report locations, and limitations. Latest execution: 26/26 scenarios
passed; the Java suite has 17 passing tests. See the [verification evidence](rate-limiter/docs/technical/VERIFICATION.md).

## Diagramas

These diagrams describe the current single-process implementation. A trusted traffic
layer is a deployment prerequisite for verified identities, not a component implemented
by this demo. The real application accepts syntactically valid client IDs.

### Arquitectura

```mermaid
flowchart LR
    Client[Cliente HTTP]
    Trusted["Capa de tráfico confiable: fuera del prototipo"]
    subgraph Java["Un proceso Java / Spring Boot MVC"]
        Interceptor[AdmissionInterceptor]
        Demo["DemoController: /api/demo"]
        Health["Health: /health sin cuota"]
        Config["Configuración validada al iniciar"]
        Limiter[RateLimiter]
        Registry["Registro acotado + lock del registro"]
        Bucket["TokenBucket por cliente + lock propio"]
        Error["ApplicationErrors: error genérico 500"]
        Config --> Limiter
        Interceptor -->|acquire| Limiter
        Limiter --> Registry
        Registry --> Bucket
        Interceptor -->|permitida| Demo
        Interceptor -->|400 / 429 / 503| Client
        Demo -->|200| Client
        Error -->|500| Client
    end
    Client -->|GET /api/demo| Interceptor
    Client -->|GET /health| Health
    Trusted -.->|validar y suministrar ID en un despliegue real| Interceptor
```

The adapter calls `RateLimiter.acquire`. Core classes depend on Java only, not
Spring or HTTP.

### Flujo de admisión

```mermaid
flowchart TD
    Request[Solicitud a un handler de negocio] --> Method{Método GET válido?}
    Method -->|No| Metadata["HEAD/otros: 405; OPTIONS: metadatos sin cuota"]
    Method -->|Sí| Identity{Un único ID válido?}
    Identity -->|No| Invalid[400 invalid_client_id]
    Identity -->|Sí| Pin["Lock registro: buscar bucket o intentar crear/reclamar"]
    Pin --> Room{Bucket disponible?}
    Room -->|No| Full[503 client_capacity_exceeded]
    Room -->|Sí| Reference["Incrementar referencias y liberar lock del registro"]
    Reference --> Lock[Tomar lock del cliente]
    Lock --> Refill[Reponer crédito según tiempo monotónico y limitar al máximo]
    Refill --> Credit{Hay un token?}
    Credit -->|Sí| Spend[Consumir un token]
    Credit -->|No| Delay[Calcular espera hasta el próximo token]
    Spend --> ReleaseA["Liberar lock cliente; decrementar referencia bajo lock registro"]
    Delay --> ReleaseB["Liberar lock cliente; decrementar referencia bajo lock registro"]
    ReleaseA --> Execute[Ejecutar endpoint: éxito 200]
    ReleaseB --> Reject[429 rate_limit_exceeded + Retry-After]
```

Health and unknown routes do not enter this business flow. Locks and references are
released with `finally`, including on unexpected failures; application errors map
to a generic 500. Admission remains charged if the endpoint subsequently fails.

### Concurrencia y ciclo de vida

```mermaid
sequenceDiagram
    participant A1 as Solicitud Alice 1
    participant A2 as Solicitud Alice 2
    participant B as Solicitud Bob
    participant R as Registro
    participant BA as Bucket Alice
    participant BB as Bucket Bob
    A1->>R: pin Alice: referencias +1 bajo lock breve
    R-->>A1: bucket
    A1->>BA: tomar lock propio
    A2->>R: pin Alice: referencias +1 bajo lock breve
    R-->>A2: mismo bucket
    A2->>BA: esperar lock de Alice
    B->>R: pin Bob bajo lock breve
    R-->>B: bucket Bob
    B->>BB: tomar lock, contabilizar y liberar
    B->>R: unpin Bob
    Note over B,BB: Bob progresa mientras Alice 2 espera
    A1->>BA: contabilizar y liberar
    A1->>R: unpin Alice 1
    BA-->>A2: lock disponible
    A2->>BA: contabilizar y liberar
    A2->>R: unpin Alice 2
    Note over R,BA: Limpieza sólo si referencias = 0 y bucket lleno
    Note over R,BB: La limpieza usa tryLock sin esperar y no elimina buckets en uso
```

No request holds a bucket lock while acquiring the registry lock. Registry lookups
still coordinate briefly, and reclamation can scan O(N) entries. Details and proof
obligations are in [DESIGN.md](rate-limiter/DESIGN.md).

## Documentation

Review in this order:

1. [Business requirements](rate-limiter/docs/business/REQUIREMENTS.md): purpose, scope, and expected outcomes.
2. [Usage options](rate-limiter/docs/business/USAGE-OPTIONS.md): how the three integration options work and what they mean for the project.
3. [Decisions](rate-limiter/docs/technical/DECISIONS.md): choices, recommendations, and approval status.
4. [Technical specification](rate-limiter/docs/technical/SPEC.md): precise behavior and acceptance criteria.
5. [Implementation plan](rate-limiter/docs/delivery/PLAN.md): delivery steps and verification.

See the [documentation guide](rate-limiter/docs/README.md) for document ownership
and the process for keeping requirements, implementation, and tests aligned.

The documents record approved individual decisions and their consolidated design.
Work continues through the approved plan without routine approval pauses. If a
decision changes, update affected documents before changing behavior.

## Collaboration agreement

- The owner makes meaningful decisions and reviews understanding and submission readiness.
- The assistant implements, recommends alternatives, documents, tests, and follows
  the challenge rules.
- Ask at meaningful deviations/crossroads. Phase milestones are progress records,
  not automatic approval stops when work follows the approved plan.
- Explain options, consequences, and a recommendation before asking for a decision.
- Approval of a phase does not imply approval of an unreviewed change in scope.
- Do not begin implementation until the complete specification is approved.

## Challenge requirements

Source: the interview challenge brief supplied by the owner (not redistributed here).

- Implement a working prototype of one problem from Alex Xu's Volume 1.
- Java is one of the permitted languages.
- The code must build and pass tests; core logic needs comprehensive tests.
- Handle errors correctly; use concurrency when needed; avoid overengineering.
- Create a new repository and a folder named for the problem.
- Include `DESIGN.md` in that folder, explaining architecture, tradeoffs, and AI use.
- Clearly comment any AI-generated code the owner does not understand. Keep such
  code to a minimum and review understanding before submission.
- Logging, metrics, deployment ease, and defensive design are desirable extras.
- Submit the repository link. Publication/submission is a separate owner decision.

The project is published at [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL);
local `main` tracks `origin/main`.
[DESIGN.md](rate-limiter/DESIGN.md) describes the implemented architecture, tradeoffs,
and AI usage. The owner understanding review is in progress.

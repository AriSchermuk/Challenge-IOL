# IOL system design challenge

A single-process Java 21 token bucket rate limiter embedded in a Spring Boot MVC
demo API. It includes per-client locking, validated client-ID headers, a bounded
in-memory registry, Docker packaging, and line/branch coverage reporting.

Java tests, Docker image/runtime checks, and Python scenarios pass. See the
[project README](rate-limiter/README.md) for commands and the
[verification record](rate-limiter/docs/technical/VERIFICATION.md) for evidence.

Public repository: [AriSchermuk/Challenge-IOL](https://github.com/AriSchermuk/Challenge-IOL).
Multiple-instance support, configurable algorithms, and client/endpoint-specific
policies are future extensions. CI, metrics, and a coverage threshold are not included.

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
3. [Decisions](rate-limiter/docs/technical/DECISIONS.md): technical choices, alternatives, and tradeoffs.
4. [Technical specification](rate-limiter/docs/technical/SPEC.md): precise behavior and acceptance criteria.
5. [Implementation plan](rate-limiter/docs/delivery/PLAN.md): delivery steps and verification.

See the [documentation guide](rate-limiter/docs/README.md) for document responsibilities
and the process for keeping requirements, implementation, and tests aligned.

## Challenge context

This project implements the rate limiter problem from Alex Xu's *System Design
Interview*, Volume 1. The challenge calls for working, tested code and a
`DESIGN.md` describing architectural choices, tradeoffs, and AI use.

[DESIGN.md](rate-limiter/DESIGN.md) describes the implementation and includes the
AI-use disclosure. Technical requirements and verification evidence are linked above.

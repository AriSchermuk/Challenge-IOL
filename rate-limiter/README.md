# Java token bucket rate limiter

A Spring Boot MVC demo API with per-client quotas, bounded in-memory tracking,
and independently implemented token bucket logic. Java 21 is required.

See the [architecture, admission flow, and concurrency diagrams](../README.md#diagramas).

## Build and run

From this directory:

```sh
./mvnw clean verify
java -jar target/rate-limiter-0.1.0-SNAPSHOT.jar
```

Alternatively, use `./mvnw spring-boot:run`. Maven Wrapper 3.3.4 downloads the
checksum-pinned Maven 3.10.0 distribution. The first build needs internet access.
Tests use ephemeral local ports; Docker is not required for Java verification.

```sh
curl -i http://localhost:8080/health
curl -i -H 'X-Client-Id: alice' http://localhost:8080/api/demo
curl -i -H 'X-Client-Id: bob' http://localhost:8080/api/demo
```

Health returns `{"status":"ok"}`. An admitted demo request returns
`{"message":"allowed"}`. Missing/invalid identity returns 400. Quota exhaustion
returns 429 with `{"error":"rate_limit_exceeded"}` and `Retry-After` seconds.
Tracking saturation returns 503 for new clients. Health never consumes tokens.

To observe rejection deterministically by hand, launch with a one-token policy:

```sh
java -jar target/rate-limiter-0.1.0-SNAPSHOT.jar --limiter.capacity=1 --limiter.refill-tokens-per-second=1
```

Send two requests for `alice` within one second: the first is allowed and the second
is rejected. Wait at least one second and retry. `bob` has its own allowance.
For a tracking-saturation demonstration, also pass `--limiter.max-clients=1` and
request `bob` immediately after consuming `alice`'s token.

## Configuration

All settings are read at startup; changing the policy requires restart.

| Property | Environment variable | Default |
| --- | --- | --- |
| `limiter.capacity` | `LIMITER_CAPACITY` | 10 tokens |
| `limiter.refill-tokens-per-second` | `LIMITER_REFILLTOKENSPERSECOND` | 2 tokens/second |
| `limiter.max-clients` | `LIMITER_MAXCLIENTS` | 10,000 |
| `server.port` | `SERVER_PORT` | 8080 |

Limiter settings must be integers between 1 and 1,000,000. Invalid settings fail
startup. These bounds are validation limits, not tested throughput guarantees.
Admission spends one token even when endpoint work subsequently fails. Rejected
requests spend none and are not queued. All business endpoints share each client's
bucket; the demo is the only business endpoint in the production application.

## Docker

Image build and runtime verification passed on the local Colima Docker engine.
The build runs all 17 tests; HTTP behavior, non-root execution, shutdown, and coverage
export were checked. See [local environment setup](docs/technical/LOCAL-ENVIRONMENT.md)
for installed tools and restart commands (`colima start` after a reboot).

```sh
docker build -t iol-rate-limiter:local .
docker run --rm -p 8080:8080 -e LIMITER_CAPACITY=10 iol-rate-limiter:local
```

The multi-stage builder runs the full Maven verification. The runtime uses a pinned
Temurin 21 JRE image, runs as UID/GID 10001, and includes only the application JAR.
Use `docker stop` to request graceful shutdown. The build needs network access for
images and Maven artifacts. To export the container build's coverage report:

```sh
docker build --target reports --output type=local,dest=target/container-coverage .
```

## Tests and coverage

`./mvnw clean verify` runs unit/concurrency tests through Surefire and real-server
HTTP integration tests through Failsafe. JaCoCo merges their execution data into
`target/site/jacoco/index.html` and `jacoco.xml`. No application classes are excluded
and no percentage threshold is enforced. See the [verification record](docs/technical/VERIFICATION.md)
for measured results, reviewed coverage gaps, and completed container checks.

## Casuísticas visibles con Python

Desde la raíz del repositorio:

```sh
python3 rate-limiter/scripts/probar_api.py
```

O desde este directorio: `python3 scripts/probar_api.py`. Requiere Python 3.9+
y Docker funcionando; no instala paquetes pip. Usa el contexto Docker activo.
Podés seleccionar otro con `--docker-context colima`, reconstruir la imagen con
`--build` o elegir el directorio de resultados con `--output /ruta/destino`.
Si la imagen no existe, el script la construye automáticamente.

La suite crea y elimina dos contenedores temporales con puertos aleatorios en
localhost. No reinicia otros servicios ni modifica el estado de una API existente.
Muestra para cada caso lo esperado y lo obtenido, y genera:

- `target/casuisticas/resultados.html`: reporte para abrir en el navegador.
- `target/casuisticas/resultados.json`: resultados estructurados.

Incluye 26 casos: identidad y validación, métodos/rutas, ráfagas, aislamiento de
clientes, Retry-After, reposición, límite de acumulación, 20 solicitudes concurrentes,
saturación del registro y reclamación segura. La salida es 0 si todos pasan y 1 si
hay fallos; una interrupción del usuario devuelve 130. Limpia sus contenedores incluso
si falla una prueba o se interrumpe la ejecución normalmente.

Son pruebas de caja negra con tiempo real: si un lote tarda un segundo o más, el
script informa que pudo haber reposición durante la medición en lugar de sacar una
conclusión ambigua. Complementan las pruebas Java con reloj controlado; no miden
rendimiento ni aportan datos a JaCoCo. `./mvnw clean` elimina los reportes bajo target;
podés volver a ejecutar la suite o seleccionar otro directorio de salida.

## Limits and trust

- Buckets live in one process. Restart resets allowances; independent instances
  each grant their own quota. Distributed coordination is future work.
- `X-Client-Id` must be one 1-64 character ASCII letters/digits/underscore/hyphen
  value, case-sensitive. This validates syntax, not identity. A production traffic
  layer should approve identities, replace caller-controlled headers, and prevent
  direct bypass. That layer is not part of this demo.
- The hard tracking limit and 503 policy are business decisions. Full, unused
  buckets can be reclaimed; depleted or referenced buckets cannot. Increasing
  supported usage requires a resource/business review.
- Per-client accounting is concurrent, but registry operations require coordination.
  On-demand cleanup can scan O(N) entries and briefly delay lookups. No throughput,
  fairness, persistence, or production availability guarantee is made.
- HEAD cannot execute the demo operation; OPTIONS returns metadata only. Unknown
  routes and unsupported methods do not consume quota.

Read [DESIGN.md](DESIGN.md), the [business requirements](docs/business/REQUIREMENTS.md),
and the [technical specification](docs/technical/SPEC.md) for rationale and future work.
Metrics, CI, and an enforced coverage threshold remain deferred. Public GitHub
publication is authorized; the final understanding/submission review is still in progress.

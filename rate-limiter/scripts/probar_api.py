#!/usr/bin/env python3
"""Pruebas visibles de caja negra: Python estándar + Docker, sin paquetes pip."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from datetime import datetime, timezone
import html
import http.client
import json
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import time
from urllib.parse import urlsplit
import uuid

PROJECT = Path(__file__).resolve().parents[1]


class Suite:
    def __init__(self, docker, image, startup_timeout):
        self.docker = docker
        self.image = image
        self.startup_timeout = startup_timeout
        self.results = []

    def command(self, *args):
        result = subprocess.run(self.docker + list(args), capture_output=True, text=True, timeout=180)
        if result.returncode:
            raise RuntimeError(f"Docker {' '.join(args[:2])}: {result.stderr.strip()}")
        return result.stdout.strip()

    @contextmanager
    def container(self, capacity, maximum):
        name = "iol-casuisticas-" + uuid.uuid4().hex[:12]
        try:
            self.command("run", "-d", "--name", name, "-p", "127.0.0.1::8080",
                         "-e", f"LIMITER_CAPACITY={capacity}",
                         "-e", "LIMITER_REFILLTOKENSPERSECOND=1",
                         "-e", f"LIMITER_MAXCLIENTS={maximum}", self.image)
            inspection = json.loads(self.command("inspect", name))[0]
            port = inspection["NetworkSettings"]["Ports"]["8080/tcp"][0]["HostPort"]
            base = f"http://127.0.0.1:{port}"
            deadline = time.monotonic() + self.startup_timeout
            while time.monotonic() < deadline:
                try:
                    if request(base, "/health")["status"] == 200:
                        print(f"  API lista: {base} (capacidad={capacity}, tasa=1/s, clientes={maximum})", flush=True)
                        break
                except (OSError, http.client.HTTPException):
                    pass
                time.sleep(0.1)
            else:
                raise RuntimeError("La API no arrancó a tiempo. " + self.command("logs", "--tail", "20", name))
            yield base
        finally:
            # Sólo elimina el contenedor con nombre único creado por esta ejecución.
            subprocess.run(self.docker + ["rm", "-f", name], capture_output=True, timeout=30)

    def case(self, title, expected, operation):
        start = time.monotonic()
        try:
            detail = operation()
            passed, error = True, None
        except Exception as exc:
            detail, passed, error = None, False, str(exc)
        result = dict(case=title, expected=expected, passed=passed, actual=detail,
                      error=error, elapsed_ms=round((time.monotonic() - start) * 1000, 2))
        self.results.append(result)
        actual = json.dumps(detail, ensure_ascii=False) if passed else error
        print(f"[{'OK' if passed else 'FALLO'}] {title}\n      esperado: {expected}\n      obtenido: {actual}", flush=True)
        return detail


def request(base, path="/api/demo", client=None, method="GET", headers=None):
    url = urlsplit(base)
    connection = http.client.HTTPConnection(url.hostname, url.port, timeout=5)
    try:
        connection.putrequest(method, path)
        if client is not None:
            connection.putheader("X-Client-Id", client)
        for name, value in headers or []:
            connection.putheader(name, value)
        connection.endheaders()
        response = connection.getresponse()
        body = response.read().decode("utf-8")
        return dict(status=response.status, body=body,
                    headers={key.lower(): value for key, value in response.getheaders()})
    finally:
        connection.close()


def expect_response(response, status, code=None):
    if response["status"] != status:
        raise AssertionError(f"HTTP {response['status']}; esperado {status}; cuerpo={response['body']}")
    if code is not None:
        if json.loads(response["body"]) != {"error": code}:
            raise AssertionError(f"Código de error inesperado: {response['body']}")
        if response["headers"].get("cache-control") != "no-store":
            raise AssertionError("La respuesta de error no desactiva el cache")
        if not response["headers"].get("content-type", "").startswith("application/json"):
            raise AssertionError("El error no tiene Content-Type JSON")
    return {"http": status, "cuerpo": response["body"],
            "retry_after": response["headers"].get("retry-after")}


def batch(base, client, count, capacity, concurrent=False):
    start = time.monotonic()
    if concurrent:
        barrier = threading.Barrier(count, timeout=10)

        def send(_):
            barrier.wait()
            return request(base, client=client)

        with ThreadPoolExecutor(max_workers=count) as executor:
            responses = list(executor.map(send, range(count)))
    else:
        responses = [request(base, client=client) for _ in range(count)]
    duration = time.monotonic() - start
    # A diferencia de las pruebas Java, aquí el reloj es real. No atribuir al
    # algoritmo un resultado ambiguo si el entorno tarda lo suficiente para reponer.
    if duration >= 1:
        raise AssertionError(f"Lote tardó {duration:.3f}s: pudo reponer tokens. Repetir en un entorno menos cargado.")
    statuses = [r["status"] for r in responses]
    expected = {200: capacity, 429: count - capacity}
    actual = {status: statuses.count(status) for status in set(statuses)}
    if actual != expected:
        raise AssertionError(f"Distribución HTTP {actual}, esperada {expected}, en {duration:.3f}s")
    for response in responses:
        if response["status"] == 429:
            expect_response(response, 429, "rate_limit_exceeded")
            if int(response["headers"].get("retry-after", "0")) < 1:
                raise AssertionError("Retry-After debe ser positivo")
    return {"permitidas": capacity, "rechazadas": count - capacity, "duracion_ms": round(duration * 1000, 2)}


def run_cases(suite):
    print("\nCasos generales: bucket de 3 tokens, repone 1 token por segundo.\n", flush=True)
    with suite.container(3, 32) as base:
        suite.case("Salud sin identidad", '200 y {"status":"ok"}',
                   lambda: health(base))
        for title, client, headers in [
            ("ID ausente", None, []), ("ID vacío", "", []),
            ("ID con espacios", "dos clientes", []), ("ID con símbolos", "cliente!", []),
            ("Lista de IDs", "alice,bob", []), ("ID demasiado largo", "a" * 65, []),
            ("Headers duplicados", "alice", [("X-Client-Id", "bob")])
        ]:
            suite.case(title, "400 invalid_client_id; no-store; JSON",
                       lambda c=client, h=headers: expect_response(request(base, client=c, headers=h), 400, "invalid_client_id"))
        suite.case("ID válido de longitud máxima", "200 con 64 caracteres",
                   lambda: expect_response(request(base, client="a" * 64), 200))
        suite.case("Ruta inexistente", "404", lambda: expect_response(request(base, "/api/inexistente", "metodos"), 404))
        for method, status in [("POST", 405), ("HEAD", 405), ("OPTIONS", 200)]:
            suite.case(f"Método {method}", f"{status}; no consume tokens",
                       lambda m=method, s=status: expect_response(request(base, client="metodos", method=m), s))
        suite.case("Métodos y rutas anteriores no gastaron cuota", "3 éxitos y luego 429",
                   lambda: batch(base, "metodos", 4, 3))
        suite.case("Ráfaga inicial y agotamiento", "3 éxitos y luego 429",
                   lambda: batch(base, "rafaga", 4, 3))
        suite.case("Cliente independiente", "200 aunque rafaga esté agotado",
                   lambda: expect_response(request(base, client="otro"), 200))
        suite.case("Mayúsculas y minúsculas son identidades distintas", "200 para RAFAGA",
                   lambda: expect_response(request(base, client="RAFAGA"), 200))
        suite.case("Health no restaura ni consume cuota", "health 200, cliente rafaga sigue en 429",
                   lambda: health_does_not_change_quota(base))
        suite.case("Recuperación respetando Retry-After", "200 después de la espera indicada",
                   lambda: retry(base))
        suite.case("Concurrencia sobre un mismo cliente", "20 solicitudes: exactamente 3 éxitos y 17 rechazos",
                   lambda: batch(base, "concurrente", 20, 3, concurrent=True))
        suite.case("El tiempo ocioso no acumula por encima de capacidad", "Tras reposición completa: 3 éxitos y 1 rechazo",
                   lambda: capped(base))
    print("\nSaturación de memoria: bucket de 3 tokens y máximo 1 cliente.\n", flush=True)
    with suite.container(3, 1) as base:
        suite.case("ID inválido no ocupa un lugar", "400 y luego 200 para un cliente válido",
                   lambda: invalid_does_not_allocate(base))
        suite.case("Registro lleno rechaza identidades nuevas", "503 client_capacity_exceeded",
                   lambda: expect_response(request(base, client="nuevo"), 503, "client_capacity_exceeded"))
        suite.case("Cliente existente sigue utilizable", "200 para existente aun con registro lleno",
                   lambda: expect_response(request(base, client="existente"), 200))
        suite.case("Health funciona aun con registro lleno", "200 sin identidad", lambda: health(base))
        suite.case("Reclamación segura tras reponer bucket", "200 para nuevo cuando existente vuelve a estar lleno",
                   lambda: reclaim(base))


def health(base):
    response = request(base, "/health")
    result = expect_response(response, 200)
    if json.loads(response["body"]) != {"status": "ok"}:
        raise AssertionError("Cuerpo de salud inesperado")
    return result


def health_does_not_change_quota(base):
    health(base)
    return expect_response(request(base, client="rafaga"), 429, "rate_limit_exceeded")


def retry(base):
    response = request(base, client="rafaga")
    expect_response(response, 429, "rate_limit_exceeded")
    seconds = int(response["headers"]["retry-after"])
    if not 1 <= seconds <= 3:
        raise AssertionError(f"Retry-After inesperado: {seconds}")
    print(f"      Esperando {seconds + 0.1:.1f}s según Retry-After…", flush=True)
    time.sleep(seconds + 0.1)
    return expect_response(request(base, client="rafaga"), 200)


def capped(base):
    request(base, client="tope")
    time.sleep(3.1)
    return batch(base, "tope", 4, 3)


def invalid_does_not_allocate(base):
    expect_response(request(base, client="invalido!"), 400, "invalid_client_id")
    return expect_response(request(base, client="existente"), 200)


def reclaim(base):
    print("      Esperando 3.1s para garantizar reposición completa…", flush=True)
    time.sleep(3.1)
    return expect_response(request(base, client="nuevo"), 200)


def report(results, directory, image):
    directory.mkdir(parents=True, exist_ok=True)
    passed = sum(r["passed"] for r in results)
    payload = dict(timestamp=datetime.now(timezone.utc).isoformat(), image=image,
                   passed=passed, failed=len(results) - passed, results=results)
    (directory / "resultados.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    rows = []
    for r in results:
        actual = r["error"] if not r["passed"] else json.dumps(r["actual"], ensure_ascii=False)
        rows.append("<tr><td>" + ("✅ OK" if r["passed"] else "❌ FALLO") + "</td><td>" +
                    html.escape(r["case"]) + "</td><td>" + html.escape(r["expected"]) +
                    "</td><td><code>" + html.escape(str(actual)) + "</code></td><td>" + str(r["elapsed_ms"]) + "</td></tr>")
    page = '''<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Casuísticas del rate limiter</title><style>body{font:16px system-ui;margin:32px;color:#152437;background:#f7f9fc}h1{margin-bottom:8px}table{border-collapse:collapse;background:white;width:100%}td,th{padding:12px;border:1px solid #dde3eb;text-align:left;vertical-align:top}code{overflow-wrap:anywhere}header{margin-bottom:24px}.table{overflow:auto}</style>
<header><h1>Casuísticas del rate limiter</h1><p>''' + str(passed) + " correctas · " + str(len(results) - passed) + " fallidas</p><p>" + html.escape(payload["timestamp"]) + '''</p><p>Pruebas HTTP con reloj real, contenedores temporales y estado aislado. No sustituyen las pruebas Java con tiempo controlado.</p></header>
<div class="table"><table><thead><tr><th>Resultado</th><th>Caso</th><th>Esperado</th><th>Obtenido</th><th>ms</th></tr></thead><tbody>''' + "".join(rows) + "</tbody></table></div></html>"
    (directory / "resultados.html").write_text(page, encoding="utf-8")
    print(f"\nResultado: {passed}/{len(results)} correctas.\nHTML: {directory / 'resultados.html'}\nJSON: {directory / 'resultados.json'}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="iol-rate-limiter:local", help="Imagen Docker a probar")
    parser.add_argument("--docker-context", help="Contexto Docker; por defecto usa el activo")
    parser.add_argument("--build", action="store_true", help="Reconstruir la imagen antes de probar")
    parser.add_argument("--output", type=Path, default=PROJECT / "target" / "casuisticas", help="Directorio para reportes")
    parser.add_argument("--startup-timeout", type=float, default=45, help="Segundos máximos para iniciar cada API")
    args = parser.parse_args()
    if args.startup_timeout <= 0:
        parser.error("--startup-timeout debe ser positivo")
    executable = shutil.which("docker")
    if not executable:
        parser.error("No se encontró docker en PATH. Ver docs/technical/LOCAL-ENVIRONMENT.md")
    docker = [executable] + (["--context", args.docker_context] if args.docker_context else [])
    suite = Suite(docker, args.image, args.startup_timeout)
    interrupted = False
    try:
        suite.command("info", "--format", "{{.ServerVersion}}")
        image_exists = subprocess.run(docker + ["image", "inspect", args.image], capture_output=True, timeout=30).returncode == 0
        if args.build or not image_exists:
            print("Construyendo imagen y ejecutando pruebas Java…", flush=True)
            subprocess.run(docker + ["build", "-t", args.image, str(PROJECT)], check=True)
        run_cases(suite)
    except KeyboardInterrupt:
        interrupted = True
        suite.case("Ejecución interrumpida", "Completar la suite", lambda: fail("Interrumpida por el usuario"))
    except Exception as exc:
        suite.case("Infraestructura de prueba", "Docker y API disponibles", lambda: fail(str(exc)))
    finally:
        report(suite.results, args.output, args.image)
    return 130 if interrupted else (0 if suite.results and all(r["passed"] for r in suite.results) else 1)


def fail(message):
    raise RuntimeError(message)


if __name__ == "__main__":
    sys.exit(main())

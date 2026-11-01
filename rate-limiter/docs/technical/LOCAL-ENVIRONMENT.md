# Local development environment

Docker was installed for this workstation on 2026-11-01. Application dependencies and
images remain separate from these host tools.

This is a record of the author workstation; another developer may use any compatible
Docker engine. Colima is not an application dependency.

## Installed tooling

| Tool | Version / location |
| --- | --- |
| Docker CLI | 29.9.2, `/usr/local/bin/docker` |
| Docker Engine | 29.5.2, inside Colima VM |
| Colima | 0.10.3, `/usr/local/bin/colima` |
| Lima | 2.2.1, `/usr/local/bin/limactl` |
| Buildx | 0.38.1, user Docker CLI plugin |
| Compose | 5.6.1, user Docker CLI plugin |

Official prebuilt binaries were used after Homebrew attempted source compilation
on this macOS version. The superseded Homebrew build was stopped. Colima/Lima and
plugin checksums were verified. Docker Desktop was not installed.

The Colima profile uses 2 CPUs, 4 GiB RAM, and a 30 GiB data disk. It uses macOS
Virtualization.framework and virtiofs. VM state persists under `~/.colima`; stopping
the VM preserves its images and data. Docker CLI plugins are in
`~/.docker/cli-plugins`. The existing default context was preserved.

## Everyday commands

```sh
colima start
docker context use colima
docker info
```

The `colima` context points to the Unix socket
`~/.colima/default/docker.sock`. Use `docker --context colima ...` to select it
explicitly without changing the active context. No system-wide Docker socket
symlink was created. Other applications should use the context or that socket.

From `rate-limiter`:

```sh
docker build -t iol-rate-limiter:local .
docker run --rm -p 8080:8080 iol-rate-limiter:local
```

Stop individual containers with `docker stop <container>`. Stop the whole VM when
finished with `colima stop`; this affects all containers on this Colima profile.
No automatic-login startup service was enabled. After reboot, run `colima start`.
Do not use `colima delete` as a routine stop command: deletion removes VM state.

## Verification

`docker version`, `docker info`, Buildx/Compose version checks, and
`docker run --rm hello-world` succeeded. The engine was left running. Application
build/runtime results are recorded separately in [VERIFICATION.md](VERIFICATION.md).

Setup references: [Colima installation](https://colima.run/docs/installation/),
[Lima installation](https://lima-vm.io/docs/installation/).

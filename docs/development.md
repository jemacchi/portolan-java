# Development

The project uses Maven and targets Java 17.

## Setup

```bash
make setup
```

This downloads dependencies and compiles the project without running the full
verification phase.

## Test and coverage

```bash
make test
make coverage
```

`make coverage` runs `mvn verify`. JaCoCo writes the HTML report to
`target/site/jacoco/index.html` and enforces 100% line and branch coverage.

## Build and install

```bash
make build
make install
```

`make build` creates the project artifact under `target/`. `make install`
installs the snapshot into the local Maven repository.

## Clean local artifacts

```bash
make clean
```

This removes Maven build output under `target/`.

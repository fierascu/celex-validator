# celex-validator

A [Quarkus](https://quarkus.io/) REST service that validates and parses [EUR-Lex CELEX numbers](https://eur-lex.europa.eu/content/help/eurlex-content/celex-number.html).  
It exposes a small JSON API and a built-in browser UI for interactive validation.

## Requirements

- Java 21+
- Maven 3.9+

## Running in development mode

```bash
mvn quarkus:dev
```

The application starts on **http://localhost:8080**.  
Hot-reload is active: changes to Java sources and static resources are picked up automatically without a restart.

The browser UI is served at **http://localhost:8080/index.html**.

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/validate?celex=<number>` | Validate and parse a CELEX number |
| `GET` | `/api/sectors` | List all known sector codes |
| `GET` | `/api/document-types[?sector=<code>]` | List document type codes, optionally filtered by sector |

### Example

```
GET /api/validate?celex=32006L0112
```

```json
{
  "valid": true,
  "input": "32006L0112",
  "sector": "3",
  "year": "2006",
  "documentTypeCode": "L",
  "number": "0112",
  ...
}
```

## Building

```bash
mvn package
```

The runnable JAR is produced in `target/quarkus-app/`.

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

# celex-validator

A [Quarkus](https://quarkus.io/) REST service that validates and parses [EUR-Lex CELEX numbers](https://eur-lex.europa.eu/content/help/eurlex-content/celex-number.html).
It exposes a small JSON API and a built-in browser UI for interactive validation.

- Clean separation of parsing / URL-building / HTTP layer / UI.
- Sector-by-sector parsing mirrors the real EUR-Lex spec reasonably faithfully (treaties, external agreements, legislation, preparatory acts, case law, national implementation, parliamentary questions, OJ C/EFTA).
- Good use of Optional, immutable records, enums with lookup helpers.
- Parameterized tests covering valid/invalid edge cases.
- Warns when a legislation CELEX's year is current/future (likely not yet published) and cross-checks existence via CELLAR's public SPARQL endpoint rather than scraping EUR-Lex HTML (which is WAF-protected) — a pragmatic workaround, documented in the class Javadoc.
- Full keyboard-accessibility test suite in `e2e/` using [Playwright](https://playwright.dev/), covering tab order, ARIA attributes, live regions, and debounced input — runs against both Chromium and Firefox.

## Requirements

### Backend (Quarkus)

- Java 25+
- Maven 3.9+

### End-to-end tests (Playwright)

- Node.js 18+
- Browsers installed via `npx playwright install`

## Running in development mode

```bash
mvn quarkus:dev
```

The application starts on **http://localhost:8080**.
Hot-reload is active: changes to Java sources and static resources are picked up automatically without a restart.

The browser UI is served at **http://localhost:8080/index.html**.

## API endpoints

| Method | Path                                  | Description                                             |
|--------|---------------------------------------|---------------------------------------------------------|
| `GET`  | `/api/validate?celex=<number>`        | Validate and parse a CELEX number                       |
| `GET`  | `/api/sectors`                        | List all known sector codes                             |
| `GET`  | `/api/document-types[?sector=<code>]` | List document type codes, optionally filtered by sector |

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

## Tests

### Unit & integration tests (JUnit 5 + REST Assured)

```bash
mvn test
```

Covers parsing logic, REST endpoints, security headers, and ArchUnit layer rules.

### End-to-end / accessibility tests (Playwright)

The `e2e/` directory contains a standalone Node.js project with 49 Playwright tests
that verify keyboard accessibility, ARIA attributes, and UI behavior in both
Chromium and Firefox.

**One-time setup** (install browsers):

```bash
cd e2e
npm install
npx playwright install chromium firefox
```

**Run tests:**

```bash
cd e2e
npm test
```

If Quarkus is already running on port 8080 it is reused; otherwise Playwright
starts `mvn quarkus:dev` automatically and stops it when the run finishes
(first start can take a minute or two while Maven resolves dependencies).

**Headed mode** (watch the browser):

```bash
cd e2e
npm run test:headed
```

**HTML report** (after a test run):

```bash
cd e2e
npm run test:report
```

#### Test groups

| Group                      | What is verified                                                     |
|----------------------------|----------------------------------------------------------------------|
| Page load                  | Auto-focus on input, `aria-live`, `role="alert"`, page title         |
| Tab order                  | Input → Validate → Clear → examples toggle; Shift+Tab backwards      |
| Keyboard activation        | Enter/Space on input, Validate button, Clear button                  |
| Example chips              | Toggle open/close; chip fills input and triggers validation          |
| Validation results         | Valid/invalid badge, `aria-invalid`, error message, segment display  |
| Details panel              | Collapsed by default; expand/collapse via click, Enter, Space        |
| Clear button               | Resets value, badge, error, visual display, `aria-invalid`           |
| Debounced live validation  | ≥5 chars fires auto-validate; <5 chars does not                      |
| ARIA attributes            | `aria-controls`, `role="group"`, visual display label, table caption |
| Reference section          | Keyboard-open `<details>`, sector grid loads, filter focusable       |
| Focus-visible (structural) | `:focus-visible` CSS rule declared in the page                       |

## Building

```bash
mvn package
```

The runnable JAR is produced in `target/quarkus-app/`.

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

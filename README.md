# Report Generator

Upload an Excel / CSV / text file, get three different HTML reports (preview in the browser, then download).

## Run
- Eclipse: File > Import > Maven > Existing Maven Projects, set the project JRE to Java 21, run `ReportGeneratorApplication`.
- Command line (JDK 21 + Maven): `mvn spring-boot:run`
- Open http://localhost:8080 (a sample sales file is built in).

## Flow
`POST /api/jobs` (upload, returns jobId) -> `GET /api/jobs/{id}` (progress, polled by the page) ->
`GET /api/jobs/{id}/reports/{1|2|3}/preview` and `/download`.

## Code map
| Package | Role |
|---|---|
| `parse` | `FileParser` (CSV via Commons CSV, Excel via Apache POI), `ValueParsers` (numbers, dates) |
| `analysis` | `TableAnalyzer` decides NUMBER / DATE / CATEGORY / TEXT per column |
| `report` | `ReportService` fills `resources/report-templates/*.html` + `common.js` with the data (JSON) |
| `job` | `JobService` runs uploads on thread pools; `Job` holds progress and results |
| `web` | `ReportController` REST API |

## Concepts practised
ExecutorService, CompletableFuture, ConcurrentHashMap, volatile, streams, records, enums, generics, file I/O, REST.

## Ideas for next steps
- Bundle Chart.js inside each report so it works offline.
- Let the user pick the sheet for multi-sheet workbooks.
- Add a 4th report type, saved report history, login.

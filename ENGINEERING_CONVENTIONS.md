# Rosie's books — Engineering Conventions

| Field | Value |
| --- | --- |
| Document status | Draft for review; intended to govern implementation |
| Product requirements | `PRD.md` |
| Implementation plan | `MILESTONES.md` |

## 1. Purpose and authority

This document records the default engineering practices for implementing Rosie's books. It is deliberately short and does not repeat product requirements or milestone acceptance criteria.

- `PRD.md` defines what the product must do.
- `MILESTONES.md` defines implementation scope, order, and completion criteria.
- This document defines how implementation work is designed, written, and verified.

When the documents disagree, do not silently choose one interpretation. Resolve the conflict and update the document that owns the decision. The requirement words **must**, **should**, and **may** are used deliberately: exceptions to a **must** require an explicit, recorded decision.

## 2. Application style

Use an **imperative application style**:

- Use Quarkus REST with ordinary blocking handlers and return types.
- Use checked/type-safe Qute templates for server-rendered HTML.
- Use jOOQ with generated schema types and the PostgreSQL JDBC driver.
- Do not introduce Hibernate ORM, Hibernate Reactive, reactive database clients, or `Uni`/`Multi` into application and domain APIs.
- Use ordinary Quarkus worker threads by default. Virtual threads may be adopted for blocking endpoints when measurements demonstrate useful concurrency, after verifying the selected JDK, PostgreSQL driver, jOOQ transaction behavior, pinning detection, and database/provider concurrency limits.

Quarkus REST may use a reactive engine internally; that does not require the application to expose reactive APIs. This application is small, server-rendered, database-backed, and transaction-oriented, so imperative control flow is the simpler default. Reactive APIs or virtual threads may be introduced later only in response to measured need and a recorded decision describing the affected boundary, benefit, and testing implications.

References:

- [Quarkus REST execution model](https://quarkus.io/guides/rest)
- [Quarkus reactive architecture](https://quarkus.io/guides/quarkus-reactive-architecture)

## 3. Development workflow

Use **red–green–refactor** for every observable behavior:

1. Add or change the smallest test that describes the intended behavior.
2. Run it and confirm that it fails for the expected reason.
3. Implement the smallest coherent production change that makes it pass.
4. Refactor while keeping the tests green.
5. Run the smallest relevant suite, then every broader suite required by the task.
6. Update affected documentation and shared contracts in the same change.

Additional rules:

- A defect fix must include a regression test that fails without the fix.
- Never weaken, remove, or ignore a valid test merely to make the build green.
- Tests should assert externally observable behavior and domain rules, not private implementation structure.
- Pure documentation, formatting, and mechanical configuration changes are exempt when no meaningful failing test can be written. They must still be reviewed and verified with the relevant build or validation command.
- A task is complete only when production code, tests, documentation, and migrations within its scope agree.

## 4. Architecture and dependencies

Organize production code **by feature first**, not by technical layer. The first package below the application's base package must represent a business feature or a deliberately small shared platform concern. Do not create top-level `web`, `application`, `domain`, `service`, `repository`, or `persistence` packages.

A feature starts as one flat package so its collaborating types can remain package-private. When it becomes difficult to navigate, split it into cohesive subfeatures or use-case packages rather than horizontal technical layers. A representative shape is:

```text
<base>
├── library
│   ├── api                 # small cross-feature contract, if needed
│   ├── addbook             # one vertical use-case package
│   ├── shelves             # another vertical use-case package
│   └── package-info.java   # feature boundary and allowed dependencies
├── identity
├── provider
└── platform                # technical bootstrap shared by several features
```

Names and exact feature boundaries are selected as behavior is implemented; this example is not a required decomposition.

- Keep HTTP handlers, use-case coordination, domain rules, persistence adapters, and view models with the feature that owns them.
- Keep business rules framework-independent even when their classes are colocated with adapters. Domain code must not depend on Quarkus, Qute, HTTP, jOOQ, PostgreSQL, OIDC, or provider-specific types.
- jOOQ-generated types, persistence records, HTTP models, and provider DTOs must remain inside their owning feature's adapter code and must not become cross-feature contracts.
- Cross-feature calls must use a small API owned by the providing feature. Feature dependencies must be explicit, one-directional, and free of cycles.
- Do not create a generic `common`, `util`, or `shared` dumping ground. Code remains with its owning feature until at least two concrete consumers demonstrate a stable shared concept.
- Keep `platform` limited to genuinely cross-cutting technical bootstrap such as framework configuration, database wiring, and common error infrastructure. It must not contain business rules.
- Prefer constructor injection and immutable values.
- Introduce abstractions at real boundaries; do not add speculative layers or generic utilities.
- A new dependency requires a concrete justification. Prefer the JDK, the Quarkus platform, and already selected libraries.

### Visibility and module enforcement

- Omit the `public` modifier by default for classes, constructors, and methods used only inside one package.
- Make a type public only when it is part of an intentional cross-feature API, required by a framework contract, or must cross an internal Java package boundary.
- If an implementation type must be public because a feature has subpackages, place it in a non-exposed internal package. Its Java visibility does not make it an application API.
- Keep cross-feature API packages small and stable. Do not expose persistence records, framework types, or broad service implementations.
- Prefer package-private constructor injection; Quarkus supports a package-private sole constructor without `@Inject`.

Use ArchUnit architecture tests as the module-boundary mechanism. Mark feature roots in `package-info.java` and enforce:

- the permitted dependencies between features;
- access to another feature only through its exposed API packages;
- no cycles between feature packages;
- no access to another feature's internal implementation; and
- confinement of jOOQ and other infrastructure types to owning adapter code.

Start with one deployable/build module. Do not introduce JPMS or one build module per feature solely for encapsulation. Reconsider physical modules only if the codebase outgrows package-and-ArchUnit enforcement.

References:

- [ArchUnit module and package rules](https://www.archunit.org/userguide/html/000_Index.html)
- [Quarkus CDI reference](https://quarkus.io/guides/cdi-reference)

## 5. Transactions and persistence

Use **jOOQ as the sole application persistence query layer**, with PostgreSQL and JDBC:

- Generate jOOQ schema types from the versioned migrations as part of the reproducible build workflow.
- Treat migrations as the schema source of truth. Generated code must never define or migrate the schema.
- Use generated tables, fields, keys, and records to build type-safe SQL, then map results explicitly to application or domain types.
- Keep SQL shape visible in repository methods. Select only the columns required by a use case and avoid implicit relationship loading.
- Do not add Hibernate ORM alongside jOOQ. Two persistence models would add mapping, flushing, caching, and transaction ambiguity without a demonstrated benefit.
- Do not use handwritten JDBC or native SQL for ordinary queries. Raw SQL is permitted only when jOOQ cannot reasonably express a required PostgreSQL feature or for operational statements such as `EXPLAIN`; isolate it in the persistence layer and cover it with a PostgreSQL-backed test.

This project has several SQL-shaped requirements: effective metadata overlays, ordered authors, ownership-scoped queries, deterministic shelf ordering, partial search, projections, constraints, and query-plan verification. jOOQ makes these operations explicit while generated schema types turn many schema/query mismatches into compilation failures. An ORM would simplify some single-record writes but would add entity-state, lazy-loading, and query-shape concerns, while the hardest reads would still need custom SQL. Handwritten native queries would retain SQL control but lose most compile-time schema safety and require more manual binding and mapping.

The Quarkus jOOQ integration is a community Quarkiverse extension rather than a core Quarkus extension. Milestone 0 must pin compatible versions and prove build-time generation, injection, transaction participation, rollback, and packaged-JVM operation. If the extension is not compatible with the selected Quarkus version, configure jOOQ directly against the Quarkus-managed JDBC datasource rather than changing the persistence approach silently.

References:

- [jOOQ code generation](https://www.jooq.org/doc/latest/manual/code-generation/)
- [jOOQ generation from migration SQL](https://www.jooq.org/doc/latest/manual/code-generation/codegen-meta-sources/codegen-ddl/)
- [Quarkiverse jOOQ extension](https://github.com/quarkiverse/quarkus-jooq)

- Put transaction boundaries on application use-case methods.
- One user action that changes durable state must commit or roll back atomically.
- Repositories participate in the caller's transaction; they do not define unrelated nested transactions.
- Every public read or mutation of user-owned data must require the owning user identity.
- Query and mutate owned records using both user identity and record identity. Never authorize by record ID alone.
- Use database constraints as a final guard for important uniqueness, ownership, and integrity rules, in addition to domain validation.
- Map expected database conflicts to deterministic application errors without leaking internal details.
- Use the PostgreSQL schema as the test and production contract; do not substitute H2 for persistence tests.

Schema changes use forward, versioned migrations:

- Production schema auto-generation or automatic schema updates are forbidden.
- A migration must work on a clean database and from the preceding released schema.
- Once a migration has been shared or released, do not edit it; correct it with a later migration.
- Destructive or compatibility-sensitive migrations must document their application and rollback implications.

Reference: [Quarkus transaction guidance](https://quarkus.io/guides/transaction).

## 6. Time and dates

- Use `LocalDate` for reading start and finish dates.
- Use `Instant` in UTC for internal timestamps.
- Represent partial publication dates explicitly; never invent missing month or day components.
- Obtain the current instant or date from an injected `Clock` or an explicit browser-local date context.
- Do not call `LocalDate.now()` or `Instant.now()` inside domain logic.
- Never convert date-only values through the server timezone.
- Tests involving the current time or date must use a fixed clock and cover relevant timezone or year boundaries.

## 7. Web conventions

- Server-rendered HTML is the baseline. JavaScript is progressive enhancement only.
- Essential navigation, forms, confirmations, and state changes must have a no-JavaScript path.
- Use checked/type-safe Qute templates and dedicated view models; do not pass persistence entities directly to templates.
- GET and HEAD requests must be safe and read-only; state changes must never use GET. Server-rendered form mutations use POST because essential workflows must work with native HTML forms and without JavaScript. If an HTTP API is introduced, it should use POST, PUT, PATCH, and DELETE according to their standard semantics. Authentication, authorization, validation, and CSRF protections apply to every state-changing method.
- After a successful form mutation, use Post/Redirect/Get where practical to make refresh behavior deterministic.
- On validation failure, preserve submitted values and place useful errors beside the relevant fields.
- Treat all user and provider text as untrusted. Escape it and render descriptions and notes as plain text.
- Every mutation must receive CSRF protection once the form/security foundation is present.
- Use semantic HTML, explicit form labels, visible focus behavior, and keyboard-operable controls as baseline implementation practices even where a dedicated accessibility audit is deferred.

Reference: [Quarkus CSRF prevention](https://quarkus.io/guides/security-csrf-prevention).

## 8. Testing strategy

Use the lowest test level that proves the behavior, with broader tests at boundaries:

- **Domain tests:** fast, framework-free tests for invariants, value types, and transition matrices.
- **Persistence tests:** PostgreSQL-backed tests for mappings, migrations, queries, constraints, concurrency, rollback, ordering, and ownership.
- **Request tests:** tests for routing, binding, rendered content, validation, redirects, authorization, CSRF, and error mapping.
- **Browser tests:** focused tests for critical journeys, responsive behavior, progressive enhancement, and no-JavaScript fallbacks.
- **Integration adapters:** deterministic contract tests using local fakes or recorded fixtures. Normal automated tests must not call live OIDC or book-provider services.

For all tests:

- User-owned behavior must include a negative cross-user case where applicable.
- Failure-path tests must verify that no partial durable change remains.
- Ordering, time, generated data, and concurrency tests must be deterministic.
- Prefer representative builders and fixtures over large copied setup blocks.
- Mock external boundaries, not the application's own domain behavior.
- A task should first run its focused suite and then the documented project verification command before completion.

## 9. Security, privacy, and observability

- Enforce ownership in application and persistence operations, not only in routes or templates.
- Cross-user lookup failures must not reveal whether another user's record exists.
- Validate input at the request boundary and enforce invariants again in the domain and database where applicable.
- Fail closed when identity, ownership, or required security configuration is absent.
- Never log notes, metadata overrides, cookies, tokens, credentials, deployment allowlists, or unnecessary search content.
- Structured logs may contain correlation identifiers, safe operational identifiers, outcome categories, timing, and error categories.
- Unexpected errors must produce a correlation identifier for operators without exposing stack traces or private values to users.
- Secrets come from runtime configuration and must never be committed or included in examples as usable values.

## 10. Code and change quality

- Prefer explicit domain types over strings, booleans, and loosely related primitive parameters.
- Make absence explicit; avoid returning `null` from application and domain APIs.
- Keep methods and classes cohesive, but avoid splitting straightforward behavior across unnecessary layers.
- Use one automated formatter and one documented verification command selected during Milestone 0.
- Do not suppress compiler, static-analysis, architecture, or test failures without a recorded reason.
- Comments should explain constraints or reasoning that the code cannot express, not narrate the code.
- Each task handoff must list changed behavior, tests and checks run, migrations or configuration added, and any unresolved blocker.
- A deliberate exception to these conventions must be documented in the change. A lasting architectural exception requires a decision record.

## 11. Decisions required before implementation

The canonical product name is **Rosie's books**. Apply it consistently to user-facing text, PWA metadata, documentation, artifact/image names, and configuration prefixes. The existing mockup filename is historical and need not be renamed. Java package names should use a stable organization and product namespace selected during Milestone 0.

Exact framework versions, build tool, migration tool, formatter, static-analysis tools, and integration-test setup remain Milestone 0 decisions. Once selected, they must be pinned and documented without changing the conventions above.

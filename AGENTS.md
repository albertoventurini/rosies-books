# Project guidance

## Overview

- Rosie's books is a mobile-first, private PWA for tracking To Read, Reading, and Finished books.
- The initial deployment serves two allowlisted users whose libraries must remain completely isolated.
- The stack is Java, imperative Quarkus REST, checked Qute templates, jOOQ, PostgreSQL, and Docker Compose.
- Pages are server-rendered; JavaScript is limited to progressive enhancement, and existing libraries do not depend on the external book provider.

## Working agreements

- Implement only the requested behavior; do not introduce speculative features or abstractions.
- Work red-green-refactor: first prove the intended test fails, implement, refactor, then run the relevant suites.
- After every feature, run `./mvnw verify`.
- Organize top-level packages by feature, not technical layer; colocate each feature's web, use-case, domain, and persistence code.
- Keep types package-private by default; expose only intentional cross-feature APIs or framework-required entry points.
- Keep business rules independent of Quarkus, HTTP, Qute, jOOQ, PostgreSQL, OIDC, and provider-specific types.
- Use generated jOOQ schema types and versioned migrations; do not use an ORM or H2 persistence substitutes.
- Require the owning user in every user-data read and mutation, and include negative cross-user tests.
- Use `LocalDate` for date-only values, UTC `Instant` for internal timestamps, and injected clocks for current time.
- Keep essential workflows correct without JavaScript; never mutate state through GET or HEAD.
- Never log private notes, credentials, tokens, cookies, allowlists, or unnecessary search content.
- Preserve unrelated work, update affected contracts and documentation, and report the validation performed.

## Commit messages

- Format every commit subject as `<type>(<task>): <description>`, for example `feat(2-5): add owner-scoped book deletion workflow`.

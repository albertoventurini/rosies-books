# Application architecture

Rosie's books is a single deployable Quarkus application organized by business feature. The
single Maven module is a deployment boundary, not permission for features to reach into one
another.

## Feature ownership

| Feature | Ownership | May depend on |
| --- | --- | --- |
| `library` | Editions, user-library records, shelves, search, notes, and preferences | `identity.api`, `provider.api`, `platform.api` |
| `identity` | Users, current-user resolution, authentication, and authorization | `platform.api` |
| `provider` | Provider-neutral search contracts and provider adapters | `platform.api` |
| `platform` | Shared technical bootstrap only | Nothing |

Each feature root has an `@AppModule` declaration in its `package-info.java`. Only the feature's
`api` namespace is available to another feature. `identity.api.CurrentUser`, which contains the
stable `UserId` and a validated, non-sensitive display label, is the intentional narrow
identity-to-library value: every owner-scoped library persistence and use-case operation requires
it, while authorization uses only the `UserId`. `CurrentUserProvider` is the replaceable contract
for resolving that value for the current request; an empty result always means unauthenticated and
must never cause a caller to substitute a default user. Neither contract exposes identity
persistence, HTTP, cookies, sessions, REST, or OIDC types. Other API namespaces remain empty until
a real cross-feature consumer requires a type. Application-wide architectural metadata such as
`AppModule` lives at the application root rather than in a feature.

## Package conventions

Business rules and ordinary use-case contracts stay free of Quarkus, HTTP, Qute, jOOQ, JDBC,
PostgreSQL, and OIDC types. Use package-private types unless a framework or a real cross-feature
contract requires wider visibility.

Adapters are placed under the feature that owns the behavior:

- `<feature>.web` contains REST and Qute adapters.
- `<feature>.persistence` contains jOOQ, JDBC, and PostgreSQL adapters.
- `identity.authentication` contains OIDC adapters.
- `identity.web` contains the development selector and its cookie-backed current-user adapter.
- `identity.persistence` contains identity-owned jOOQ adapters and the profile-scoped seed writer.
- `platform.web` contains shared web bootstrap only.
- `platform.health` contains side-effect-free process health checks.
- `platform.database` contains shared datasource and database bootstrap only.

Domain and use-case packages may be introduced within a feature when behavior exists; the
architecture does not require empty technical-layer packages. Provider-specific types remain
inside provider adapters and must not leak into `provider.api`.

ArchUnit checks the declared dependency graph, API-only cross-feature access, feature cycles,
infrastructure confinement, and the ownership signature rule. Any library repository or service
method accepting a `UserEdition` or `UserEditionId` must also accept `CurrentUser`; a test-only
ID-only overload proves that rule fails closed. Other violating fixtures prove that each rule
detects the failure it claims to prevent. `archunit.properties` makes applicable empty rules fail
instead of passing silently.

## Current-user adapters by profile

Development and test builds include two deterministic local users, the `/dev/users` selector, and
the unsigned alias-cookie adapter. Production builds include none of those components. They use a
provider that always returns empty, so the process can start before OIDC exists while every
identity-dependent operation remains unauthenticated. The selector routes are removed at build
time in production rather than hidden at render time.

Milestone 8 replaces the `CurrentUserProvider` implementation and development cookie/session
adapter with OIDC and a secure server-side session. The library's identity API and owner-scoped
signatures do not change.

## Adding a feature or API

To add a feature:

1. Create `<application-root>.<feature>/package-info.java` with its ownership documentation and an
   `@AppModule` declaration listing only required `<target>.api` dependencies.
2. Create and document `<feature>.api`, leaving it empty unless an existing consumer needs a type.
3. Add the feature and its dependency set to the architecture rule's feature declarations and to
   the module-declaration assertion.
4. Add positive or violating fixtures for any new dependency shape, then run `./mvnw test`.

To expose an API, first identify the consuming feature and add the narrowest contract to the
owner's `api` package. Keep provider, persistence, web, and authentication types out of the
signature. Update the owner's `@AppModule` consumer relationship only if a new graph edge is
needed, add architecture coverage, and run `./mvnw verify`.

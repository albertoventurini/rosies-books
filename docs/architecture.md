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
`api` namespace is available to another feature. Those namespaces are intentionally empty in this
scaffold: add a type only when a real cross-feature consumer requires it. Application-wide
architectural metadata such as `AppModule` lives at the application root rather than in a feature.

## Package conventions

Business rules and ordinary use-case contracts stay free of Quarkus, HTTP, Qute, jOOQ, JDBC,
PostgreSQL, and OIDC types. Use package-private types unless a framework or a real cross-feature
contract requires wider visibility.

Adapters are placed under the feature that owns the behavior:

- `<feature>.web` contains REST and Qute adapters.
- `<feature>.persistence` contains jOOQ, JDBC, and PostgreSQL adapters.
- `identity.authentication` contains OIDC adapters.
- `platform.web` contains shared web bootstrap only.
- `platform.health` contains side-effect-free process health checks.
- `platform.database` contains shared datasource and database bootstrap only.

Domain and use-case packages may be introduced within a feature when behavior exists; the
architecture does not require empty technical-layer packages. Provider-specific types remain
inside provider adapters and must not leak into `provider.api`.

ArchUnit checks the declared dependency graph, API-only cross-feature access, feature cycles, and
infrastructure confinement. Test-only violating fixtures prove that each rule detects the failure
it claims to prevent. `archunit.properties` makes applicable empty rules fail instead of passing
silently.

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

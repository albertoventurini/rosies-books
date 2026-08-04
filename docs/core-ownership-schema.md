# Core ownership schema

Tasks 1-1 through 1-4 establish the persistence and current-user boundary for shared canonical
editions, private user libraries, normalized ISBN identity, private effective metadata,
reading-state rules, and profile-scoped development identities. The manual-add workflow remains
outside this contract.

## Table and feature ownership

Identity owns `app_user` and its generated jOOQ model. `identity.api.CurrentUser`, containing a
stable `UserId`, is the owner value the library receives from identity. Library owns `cover_asset`,
`user_preference`, `edition`,
`edition_author`, `user_edition`, `user_edition_metadata_override`, and
`user_edition_author_override`, together with their generated jOOQ models.

UUIDs and timestamps are supplied by callers. Persistence code never asks PostgreSQL for a UUID or
the current time. Internal timestamps are UTC `Instant` values in Java and `timestamp with time
zone` values in PostgreSQL. Reading dates remain date-only `LocalDate` values.

## Ownership and deletion

Every private UserEdition or override repository and use-case operation accepts a `CurrentUser`
and scopes its SQL to both the contained owner ID and record identity. Preferences also require
`CurrentUser`. A foreign or unknown UserEdition therefore produces the same empty/false result.
The database applies these lifetime rules:

- Deleting a User cascades to that user's preference, UserEditions, metadata overrides, and ordered
  author overrides. It does not delete a shared Edition.
- Deleting a UserEdition cascades to its scalar and author overrides.
- Deleting an Edition cascades to canonical authors, but PostgreSQL restricts deletion while any
  UserEdition references it.
- Deleting a referenced cover sets `edition.cover_asset_id` to null. Edition deletion never deletes
  a cover automatically.

Repositories join an existing caller transaction. Multi-row Edition writes are therefore atomic
when invoked by a transactional use-case coordinator. The metadata override use case owns the
transaction that replaces a complete override snapshot and ordered author rows and then refreshes
both effective search projections. Validation or any later write failure rolls back the complete
replacement.

State updates follow the same transaction contract. The owner-scoped repository writes the state,
both reading-date columns, and a caller-supplied UTC `updated_at` in one statement. It scopes the
statement by both the current user's `UserId` and `UserEditionId`, returning false for foreign and unknown records
without revealing which case occurred. The repository never opens its own transaction, so a later
failure in the calling use case rolls the update back.

## Reading states and transitions

The framework-independent `ReadingState` model makes invalid date shapes unrepresentable:

- `ToRead` contains no dates.
- `Reading` requires one `LocalDate` start.
- `Finished` requires one `LocalDate` finish, represents an unknown start with `Optional.empty()`,
  and rejects a finish before a known start. Equal start and finish dates are valid.

State changes are planned without persistence or other side effects. Browser-local today and the
finish date are explicit inputs; the server clock and timezone are not consulted. Same-state moves
and missing or contradictory inputs are rejected. The complete cross-state matrix is:

| Source | Target | Start date result | Finish date result | Confirmation |
| --- | --- | --- | --- | --- |
| To Read | Reading | Set to supplied local today | Clear | None |
| To Read | Finished | Set to supplied optional start, or unknown | Set to supplied finish | None |
| Reading | To Read | Clear | Clear | `DISCARD_RECORDED_DATES` |
| Reading | Finished | Retain existing start; replacement is rejected | Set to supplied finish | None |
| Finished | Reading | Retain known start, otherwise set to supplied local today | Clear | None |
| Finished | To Read | Clear | Clear | `DISCARD_RECORDED_DATES` |

The planner returns a `TransitionPlan` containing the resulting validated state and an optional
confirmation requirement. A caller must obtain the indicated confirmation before persisting a
To Read plan; confirmation handling itself belongs to a later web task.

PostgreSQL repeats the domain invariants with the named `user_edition_state_dates` check for the
complete state/date-presence matrix and `user_edition_date_chronology` for date order. The original
`user_edition_state_check` continues to restrict the state vocabulary. Migration V6 adds these as
immediately validated constraints: it performs no repair or coercion, so deployment fails if a V5
database contains an incompatible historical row. Operators must investigate and correct such data
deliberately before retrying the migration.

Migration V7 adds nullable `user_edition.request_id` for idempotent manual additions and the named
owner-scoped unique constraint `user_edition_user_request_key` on `(user_id, request_id)`. The
column remains nullable so non-manual link workflows and existing rows need no synthetic request
identity.

## Canonical identifiers and metadata

Edition identity is always an application-generated UUID. ISBN value types accept ASCII digits
with whitespace or hyphen separators, normalize away those separators, validate check digits, and
uppercase a terminal ISBN-10 `X`. ISBN-13 values must use a valid 978 or 979 prefix. Supplying an
ISBN-10 derives and persists its 978 ISBN-13; supplying both identifiers requires them to describe
the same edition.

Normalized ISBN-13 is the sole ISBN lookup and uniqueness key. Looking up with ISBN-10 first
converts it to the same canonical key. A standalone valid 978 or 979 ISBN-13 is also accepted, but
an ISBN-10 is not reverse-invented from an ISBN-13-only input. Named database constraints repeat
the format, checksum, prefix, and canonical-pair consistency rules for direct writes.

Provider name and provider edition ID are either both absent or both present. Provider names are
trimmed lowercase keys; provider edition IDs are trimmed while retaining provider-defined case.
Their pair is uniquely constrained. Manual editions without these identifiers are not merged by
title or author similarity.

Canonical Edition data contains the PRD bibliographic fields and an ordered `edition_author` list.
Edition construction and the persistence adapter reject an empty canonical author list; there is
no procedural cross-row database trigger for that rule.

## Partial publication dates

`PartialPublicationDate` stores precisely one of unknown, year, year-month, or full calendar date.
The three database columns stay nullable, so no month or day is manufactured. Java construction and
PostgreSQL constraints reject missing leading components, out-of-range values, invalid month/day
combinations, and invalid leap days.

Ordering is deterministic: unknown follows every known value, supplied components compare
lexicographically, and a lower-precision prefix precedes a higher-precision value. All four
precisions map exactly to the three columns and back.

## Private overrides and search projections

The domain contract gives every supported metadata field one of three immutable states: inherited,
an overridden value, or explicit blank. It covers title, subtitle, ordered authors, format, both
ISBNs, publisher, partial publication date, page count, language, and description. At the database
boundary a false `is_overridden` flag plus null stores inherited, a true flag plus a value stores an
override, and true plus null stores explicit blank. Explicitly blank authors use a true flag and no
author rows; value overrides preserve row order. Private ISBNs are validated values but never
participate in canonical identity or uniqueness.

The pure effective-metadata resolver applies each field independently and preserves text, author
order, and partial-date precision. Optional fields may resolve empty. Effective title must contain
non-whitespace text, and effective authors must be nonempty with no blank names.

`effective_title_search` and `effective_authors_search` are private, application-maintained
UserEdition projections. Linking initially copies canonical title and ordered authors. Each
successful override save refreshes both from the same resolved metadata in the override
transaction; resetting to inherited restores the canonical projections. Unknown and foreign
UserEdition IDs both return the same non-identifying result. Lowercase-expression GIN trigram
indexes support future case-insensitive partial search without consulting a provider.

The three shelf indexes mirror their planned default order: Reading by start date, To Read by link
creation time, and Finished by finish date, each owner-scoped and with the UserEdition ID as a
stable tie-breaker.

## Stable conflicts

Adapters inspect PostgreSQL SQL state `23505` and the named constraint before translating a
failure. They expose stable persistence failures for duplicate OIDC identity, canonical ISBN-13,
provider edition identity, and a duplicate Edition link for one user. Any other database failure is
re-thrown unchanged.

## Development identities and selector

Development and test startup transactionally ensures these fixed fake identities exist in the
existing `app_user` table:

| Alias | Display label | User ID | Email |
| --- | --- | --- | --- |
| `reader-one` | Reader One | `00000000-0000-0000-0000-000000000001` | `reader-one@rosies-books.invalid` |
| `reader-two` | Reader Two | `00000000-0000-0000-0000-000000000002` | `reader-two@rosies-books.invalid` |

Their issuer is `https://oidc.rosies-books.invalid/development`; their subjects are the fixed
aliases prefixed with `development:`. The creation and update timestamp is fixed as well, making a
complete row deterministic. Re-running the seeder retains an exactly matching row. If a fixed ID
has different data, or a fixed OIDC identity belongs to another row, startup fails. Both seed
writes share one transaction, so a conflict cannot leave only one user inserted. This startup data
requires no migration and is never created in a production build.

In development and test builds, `GET /dev/users` renders the two display labels and marks the
current selection. `POST /dev/users` accepts an exact alias, writes it to the `rosies-dev-user`
cookie, and redirects to `/` with status 303. Invalid aliases return 400 without setting a cookie.
The cookie has `Path=/`, `HttpOnly`, and `SameSite=Lax`; it contains no UUID, email, or other private
data. It is intentionally unsigned because this trusted local selector already permits choosing
either seed user. It is development tooling, not authentication, and must never be adopted as the
production session design.

Missing, malformed, and unknown cookie values resolve no current user. Production builds have no
seed writer or `/dev/users` routes and use a provider that always returns empty. Consequently they
start normally before milestone 8 but deny every identity-dependent workflow. Milestone 8 replaces
only the current-user provider and session adapter; the `CurrentUser` library boundary remains
unchanged.

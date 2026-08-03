# Core ownership schema

Task 1-1 establishes the persistence boundary for shared canonical editions and private user
libraries. It intentionally does not implement ISBN checksum/conversion rules, effective-metadata
resolution after edits, the complete reading-state/date matrix, seeded users, or any web behavior.

## Table and feature ownership

Identity owns `app_user` and its generated jOOQ model. `identity.api.UserId` is the sole contract
the library needs from identity. Library owns `cover_asset`, `user_preference`, `edition`,
`edition_author`, `user_edition`, `user_edition_metadata_override`, and
`user_edition_author_override`, together with their generated jOOQ models.

UUIDs and timestamps are supplied by callers. Persistence code never asks PostgreSQL for a UUID or
the current time. Internal timestamps are UTC `Instant` values in Java and `timestamp with time
zone` values in PostgreSQL. Reading dates remain date-only `LocalDate` values.

## Ownership and deletion

Every private UserEdition or override repository operation accepts a `UserId` and scopes its SQL
to both the owner and record identity. A foreign or unknown UserEdition therefore produces the
same empty/false result. The database applies these lifetime rules:

- Deleting a User cascades to that user's preference, UserEditions, metadata overrides, and ordered
  author overrides. It does not delete a shared Edition.
- Deleting a UserEdition cascades to its scalar and author overrides.
- Deleting an Edition cascades to canonical authors, but PostgreSQL restricts deletion while any
  UserEdition references it.
- Deleting a referenced cover sets `edition.cover_asset_id` to null. Edition deletion never deletes
  a cover automatically.

Repositories join an existing caller transaction. Multi-row Edition and override writes are
therefore atomic when invoked by a transactional use-case coordinator.

## Canonical identifiers and metadata

Edition identity is always an application-generated UUID. A nullable ISBN-13 must contain exactly
13 digits and has a named unique constraint. A nullable ISBN-10 must contain ten normalized ISBN
characters, with `X` permitted only in the last position. Checksum validation and ISBN-10/13
conversion belong to task 1-2.

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

The optional one-to-one metadata-override row has an `is_overridden` flag for every supported
scalar field and the ordered author list. A false flag requires its scalar value columns to be
null. A true flag may carry a value or null; true plus null stores an explicit blank. Ordered author
override rows are children of the metadata-override row and preserve their supplied positions.

`effective_title_search` and `effective_authors_search` are private, application-maintained
UserEdition projections. Linking initially copies canonical title and ordered authors. Task 1-2
will refresh them when effective overrides change. Lowercase-expression GIN trigram indexes support
future case-insensitive partial search without consulting a provider.

The three shelf indexes mirror their planned default order: Reading by start date, To Read by link
creation time, and Finished by finish date, each owner-scoped and with the UserEdition ID as a
stable tie-breaker.

## Stable conflicts

Adapters inspect PostgreSQL SQL state `23505` and the named constraint before translating a
failure. They expose stable persistence failures for duplicate OIDC identity, canonical ISBN-13,
provider edition identity, and a duplicate Edition link for one user. Any other database failure is
re-thrown unchanged.

# Manual book entry

Task 2-3 provides an authenticated, server-rendered form that saves a manually entered book
directly. There is no confirmation or review page: a valid save creates or reuses the canonical
Edition and links it to the current user's library in one transaction.

## Browser contract

Every shelf contains an ordinary link to `GET /books/new/manual`. `GET` and its safe `HEAD`
handling render a form whose initial state is To Read. A fresh opaque UUID is included as the
hidden `requestId`. It is retained through validation failures and every form-editing POST, so a
browser retry can be identified without trusting any bibliographic field.

`POST /books/new/manual` consumes `application/x-www-form-urlencoded` data. Both routes require a
resolved `CurrentUser` and return `401 Unauthorized` when identity resolution fails. The form
fields are `requestId`, `title`, repeated `authors`, `subtitle`, `format`, `isbn10`, `isbn13`,
`publisher`, `publicationDate`, `pageCount`, `language`, `description`, `state`, `startedOn`, and
`finishedOn`. Repeated `authors` values retain browser order. Blank author rows are ignored during
validation without reordering the remaining names.

The submit button named `intent` supports these values:

| Intent | Result |
| --- | --- |
| `add-author` | Append one blank author row, up to 20 visible rows |
| `remove-author-N` | Remove zero-based row `N`, retaining at least one row |
| `change-state` | Re-render the date controls permitted by the submitted state |
| `save` | Revalidate every value and atomically add the book |

Form-editing responses return `200`. Invalid save data, a missing or malformed request UUID, or an
unsupported intent returns `400` and persists nothing. Validation failures retain the request UUID
and every raw submitted value. Every visible control has an adjacent stable error container
referenced by `aria-describedby`; invalid controls also receive `aria-invalid`. Qute escapes all
submitted content, and no workflow depends on JavaScript.

A successful save returns `303 See Other` and redirects to `/to-read`, `/reading`, or `/finished`
according to the state on the resulting persisted UserEdition. This also applies when the request
reuses an existing link; its current state determines the redirect.

## Validation and normalization

`save` validates all submitted values again. Hidden values and values previously accepted by a
form-editing action are never treated as trusted metadata.

Surrounding whitespace is removed from validated values. Blank optional strings become absent.
ISBN separators are normalized by `Isbn10` and `Isbn13`; supplying ISBN-10 derives the canonical
978 ISBN-13, and a supplied pair must agree. Publication dates accept exactly `YYYY`, `YYYY-MM`, or
`YYYY-MM-DD` and retain their original form value when validation fails. Page count is an optional
base-10 integer.

Application and domain construction share these limits:

| Value | Limit |
| --- | --- |
| Title | Required; at most 500 characters |
| Authors | 1–20 nonblank names; at most 300 characters each |
| Subtitle, format, publisher, language | At most 500 characters each |
| Description | At most 10,000 characters |
| Raw ISBN-10 and ISBN-13 inputs | At most 64 characters each |
| Page count | 1–1,000,000 |

`EditionMetadata` repeats the normalized title, author, supported text, and page-count invariants,
so callers outside the browser binding cannot construct unsupported metadata.

## State dates and default zone

The state vocabulary is `TO_READ`, `READING`, and `FINISHED`:

| State | Start date | Finish date |
| --- | --- | --- |
| To Read | Forbidden and cleared | Forbidden and cleared |
| Reading | Required | Forbidden and cleared |
| Finished | Optional | Required |

Changing between Reading and Finished retains the shared start value. A blank required start or
finish is defaulted from an injected clock in `rosies-books.default-zone`. The configuration
defaults to `Africa/Johannesburg`; the JVM ambient timezone is never consulted. Submitted nonblank
date values remain `LocalDate` values and are parsed without timezone conversion.

## Identifier resolution and owner isolation

Normalized ISBN-13 is the only canonical lookup key. This includes ISBN-13 derived from ISBN-10.
When that key already exists, saving reuses the Edition exactly as stored and never overwrites its
shared metadata. Otherwise the transaction creates a `MANUAL` Edition with ordered authors, no
provider identity, and no cover. Identifierless entries always create distinct Editions; title,
author, and other fuzzy metadata are never used to merge books.

The selected Edition is linked through an owner-scoped UserEdition with the submitted initial
state and dates. Every lookup and mutation requires `CurrentUser`. If that owner already links the
Edition, the existing UserEdition is returned without changing its state, dates, timestamps,
request UUID, private notes, or other private data. A different owner can independently link the
same canonical Edition, including while using the same request UUID.

## Retries, concurrency, and atomicity

Migration V7 adds nullable `user_edition.request_id` and the named unique constraint
`user_edition_user_request_key` on `(user_id, request_id)`. Nullable values preserve compatibility
for links created by other workflows, while manual additions record their request UUID.

The manual-add service is transactional. It takes a transaction-scoped PostgreSQL advisory lock
derived from owner and request UUID before checking for an exact retry. Repeating a completed
request therefore returns the previously created UserEdition, including for identifierless books.
Separate request UUIDs do not cause identifierless entries to merge.

ISBN Edition creation uses `INSERT ... ON CONFLICT` on canonical ISBN-13, and owner linking uses a
conflict-safe insert on `(user_id, edition_id)`. Expected concurrent submissions converge on one
applicable canonical Edition and one link for each owner instead of surfacing uniqueness races.
Edition, ordered-author, and UserEdition writes share the same transaction. A failure at any write
boundary rolls back all canonical and private rows, so no partial book can remain.

CSRF protection remains deferred to the form-security foundation milestone.

## Completed manual-library journey

The PostgreSQL-backed request suite exercises manual creation in To Read, Reading, and Finished,
exact request replay without duplication, ordinary and confirmed state changes on the same owned
IDs, stale and repeated submissions, deletion cancellation, successful permanent deletion,
failure rollback followed by retry, and cross-user denial. It uses only the two development
identities and local PostgreSQL; no provider lookup or live OIDC interaction participates.

Manual Editions created here have neither provider identity nor cover. Deleting their final
UserEdition reference through `POST /books/{userEditionId}/delete` therefore also removes the
orphan Edition and its canonical authors in the same transaction. An Edition shared by another
user is retained until its final reference is removed. Cover-asset deletion remains outside this
workflow.

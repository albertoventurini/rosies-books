# Manual book entry

Task 2-2 adds an authenticated, server-rendered way to validate and review a manually entered
book. It deliberately performs no persistence. Task 2-3 will consume the validated draft and add a
separate confirmation that writes the Edition and current user's UserEdition atomically.

## Browser contract

Every shelf contains an ordinary link to `GET /books/new/manual`. `GET` and its safe `HEAD`
handling render a form whose initial state is To Read. `POST /books/new/manual` consumes
`application/x-www-form-urlencoded` data. Both routes require a resolved `CurrentUser` and return
`401 Unauthorized` when identity resolution fails.

The form fields are `title`, repeated `authors`, `subtitle`, `format`, `isbn10`, `isbn13`,
`publisher`, `publicationDate`, `pageCount`, `language`, `description`, `state`, `startedOn`, and
`finishedOn`. Repeated `authors` values retain browser order. Blank author rows are ignored during
validation without reordering the remaining names.

The submit button named `intent` supports these values:

| Intent | Result |
| --- | --- |
| `add-author` | Append one blank author row, up to 20 visible rows |
| `remove-author-N` | Remove zero-based row `N`, retaining at least one row |
| `change-state` | Re-render the date controls permitted by the submitted state |
| `review` | Validate all fields and render either adjacent errors or the review |
| `edit` | Return from a successful review to the populated form |

Add, remove, state-change, and edit responses return `200`. Invalid review data returns `400` with
all independent field errors. Every control has an adjacent stable error container referenced by
`aria-describedby`; invalid controls also receive `aria-invalid`. Qute escapes submitted content
in both the form and review. No workflow depends on JavaScript.

## Normalization and limits

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
so callers outside this browser binding cannot construct unsupported metadata.

## State dates and default zone

The state vocabulary is `TO_READ`, `READING`, and `FINISHED`:

| State | Start date | Finish date |
| --- | --- | --- |
| To Read | Forbidden and cleared | Forbidden and cleared |
| Reading | Required | Forbidden and cleared |
| Finished | Optional | Required |

Changing between Reading and Finished retains the shared start value. A blank required start or
finish is defaulted from an injected clock in `rosies-books.default-zone`. The configuration
defaults to `Africa/Johannesburg`; the JVM ambient timezone is never consulted. This is the
application-wide fallback until per-user timezone preferences exist. Submitted nonblank date
values remain date-only values and are parsed without timezone conversion.

## Review boundary

A successful review builds an immutable, package-private draft containing normalized
`EditionMetadata` and a valid `ReadingState`. The review explicitly states that nothing has been
saved and offers only an Edit POST action carrying the form values. The resource has no repository
dependency, and request tests assert that valid review, GET, and HEAD requests create neither
Edition nor UserEdition rows.

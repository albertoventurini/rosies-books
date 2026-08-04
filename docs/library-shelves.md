# Library shelves

Task 2-1 introduces the first authenticated, user-scoped library UI. It is server-rendered and
does not require JavaScript for navigation or content.

## Routes and authentication

The browser routes are:

| Method | Route | Result |
| --- | --- | --- |
| `GET`, `HEAD` | `/` | `303 See Other` to `/reading` |
| `GET`, `HEAD` | `/reading` | Current user's Reading shelf |
| `GET`, `HEAD` | `/to-read` | Current user's To Read shelf |
| `GET`, `HEAD` | `/finished` | Current user's Finished shelf |
| `GET`, `HEAD` | `/books/{userEditionId}/state` | Shelf-change form for an owned book |
| `POST` | `/books/{userEditionId}/state` | Validate, confirm, cancel, or apply a shelf change |
| `GET`, `HEAD` | `/books/{userEditionId}/delete` | Permanent-deletion confirmation for an owned book |
| `POST` | `/books/{userEditionId}/delete` | Cancel or permanently delete an owned book |

Shelf routes return `401 Unauthorized` when `CurrentUserProvider` cannot resolve the request.
They never fall back to a development identity. In development and tests, selecting a user still
posts to `/dev/users`, sets the alias cookie, and redirects through `/`; the root redirect then
takes the browser to `/reading`.

`identity.api.CurrentUser` carries two values: the stable `UserId` used for every ownership and
authorization decision, and a normalized, nonblank display label intended for escaped UI output.
Identity adapters are responsible for supplying a non-sensitive label. The label is never used as
an ownership key.

## Shelf projection and ordering

`Shelf` is the explicit mapping between route slug, heading, active navigation item, empty-state
copy, and persisted reading state. The jOOQ shelf adapter requires `CurrentUser` and `Shelf` for
every query. Its base row query and its ordered-author queries all constrain both `user_id` and
`state`; no unowned lookup or default owner exists.

Only the shelf projection leaves persistence: the owned user-edition ID, effective title, and the
ordered effective authors. The ID is used only to link to the state-change workflow. A private
title override replaces the canonical title. When authors are overridden, the private sequence
ordered by `position` replaces the canonical sequence; otherwise canonical authors are ordered by
their `position`. Canonical edition IDs and user IDs remain internal.

Default ordering is:

| Shelf | Order |
| --- | --- |
| Reading | `started_on DESC, id ASC` |
| To Read | `created_at DESC, id ASC` |
| Finished | `finished_on DESC, id ASC` |

Finished currently renders all matching records across every year. Browser-local year controls
and annual counts are intentionally deferred to task 3-3.

## Typographic placeholders

Every shelf row in this manual-entry slice uses a typographic placeholder. The effective title and
the exact ordered author list are hashed with Java's stable `String` and `List` hash contracts via
`Objects.hash`. The nonnegative remainder selects one of six checked-in theme class names. User
text is never emitted into a `style` attribute; it appears only as Qute-escaped text, while CSS
handles wrapping, clamping, and overflow.

Stored cover selection and delivery are not part of this slice. Later shelf/cover work can choose a
stored cover without changing the title/author projection or placeholder fallback contract.

## Responsive shell

The shared stylesheet is mobile-first. Shelf navigation is an ordinary three-link bottom bar on
narrow viewports and becomes a sidebar at 48 rem. The layout includes visible keyboard focus,
safe-area padding, minimum-width protection, and text overflow handling. The manual-entry workflow
adds an ordinary link to each shelf header; book-detail, year-filter, count, layout-toggle, and shelf
mutation controls remain outside this task.

## State changes, dates, and confirmation

Each shelf row has an ordinary `Change shelf` link. The page offers only the other two shelves.
Changing the destination selector immediately shows the relevant date fields: Reading shows its
start date, while Finished shows start and finish dates. A small plain-JavaScript enhancement
handles that conditional display; the submitted workflow remains functional when JavaScript is
disabled. All six transitions use the shared domain transition planner:

| From | To | Date result |
| --- | --- | --- |
| To Read | Reading | Start defaults to today in `rosies-books.default-zone` and may be changed |
| To Read | Finished | Finish defaults to zoned today; start is optional |
| Reading | Finished | Existing start is retained; finish defaults to zoned today |
| Finished | Reading | Known start is retained; an unknown start defaults to zoned today and may be changed; finish is cleared |
| Reading | To Read | Start is cleared after confirmation |
| Finished | To Read | Known start and finish are cleared after confirmation |

Equal start and finish dates are valid; a finish before a known start is rejected. Date-only data
uses `LocalDate`. The update timestamp comes from the injected clock and is stored as a UTC instant.

A first POST to move a Reading or Finished book to To Read is a preview only. It renders the exact
stored dates that confirmation will clear and cannot mutate the row. A second POST with the fixed
confirmation intent performs the update. Cancellation is also a POST, performs no mutation, and
redirects to the book's current shelf.

Malformed IDs and records owned by another user both return the same non-identifying `404` for
view, transition, confirmation, and cancellation requests. Invalid targets, versions, dates,
intents, or date relationships return `400` with escaped submitted values and adjacent errors.

## Optimistic locking and responses

Migration V8 gives every existing and new `user_edition` a nonnegative `version`, initially zero.
The rendered form includes that value. A successful update predicates on owner ID, user-edition ID,
and expected version, then atomically changes state and dates, sets `updated_at`, and increments the
version exactly once. A stale or replayed form returns `409 Conflict` without another mutation and
asks the user to review current state. If a conditional update finds no row, an owner-scoped lookup
distinguishes a stale version from inaccessible data without disclosing whether another owner has
the ID. Unexpected failures continue through the shared correlation-ID `500` response; the
transaction preserves the prior state, dates, timestamp, and version.

Successful manual additions use `303 See Other` with `notice=book-added`, successful changes use
`notice=state-changed`, and cancellation uses `notice=state-change-cancelled`. Only those fixed
codes, plus deletion's `book-deleted` and `book-deletion-cancelled`, render a shelf status banner. A
shelf-only, plain-JavaScript enhancement removes a recognized notice parameter from browser history
immediately and removes the transient banner after five seconds. With JavaScript disabled, the
banner stays visible until navigation. Validation, conflict, and unexpected-error messages are
never transient.

## Permanent deletion

Each shelf row has an ordinary `Delete` link to a server-rendered confirmation. Viewing the page,
including its safe `HEAD` handling, never mutates data. The page shows the escaped effective title,
current shelf, and an explicit warning that dates, notes, and private metadata will be permanently
removed. Its form contains the current optimistic `version` and supports only `intent=delete` or
`intent=cancel`.

Cancellation ignores a stale but well-formed version, performs no mutation, and returns `303 See
Other` to the book's current shelf with `notice=book-deletion-cancelled`. Deletion requires the
rendered version. Success returns `303` to the former shelf with `notice=book-deleted`; a stale
form returns `409 Conflict` with a link to load a fresh confirmation. Invalid versions or intents
return `400`. Malformed, unknown, already-deleted, and cross-user IDs all return the same
non-identifying `404`.

The public deletion use case accepts `CurrentUser` on every lookup and mutation and exposes only a
deletion projection: effective title, shelf, and version. In one PostgreSQL transaction it finds
the owner-scoped private row, locks its canonical Edition, then rechecks owner, ID, and expected
version. The UserEdition delete cascades through scalar and ordered-author override rows. The
canonical Edition and ordered canonical authors are also deleted only when the Edition is manual,
has no provider identity, and has no remaining UserEdition reference. Shared, provider-origin, and
provider-identified Editions, other owners' links and private data, and cover assets are retained.
The Edition lock serializes concurrent deletion of the final shared references.

Any persistence failure uses the shared correlation-ID `500` response. The transaction rolls back
both private-row deletion and orphan cleanup, leaving the same version retryable. No migration is
needed because the existing foreign keys, cascades, restrictions, and optimistic version support
the workflow.

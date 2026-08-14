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
| `GET`, `HEAD` | `/finished[?year=YYYY]` | Current user's Finished shelf for one year |
| `GET`, `HEAD` | `/search?q=QUERY` | Current user's cross-shelf search results |
| `GET`, `HEAD` | `/books/{userEditionId}/state` | Shelf-change form for an owned book |
| `POST` | `/books/{userEditionId}/state` | Validate, confirm, cancel, or apply a shelf change |
| `GET`, `HEAD` | `/books/{userEditionId}/delete` | Permanent-deletion confirmation for an owned book |
| `POST` | `/books/{userEditionId}/delete` | Cancel or permanently delete an owned book |
| `GET`, `HEAD` | `/books/{userEditionId}` | Detail page for an owned book |
| `GET`, `HEAD` | `/books/{userEditionId}/cover` | Stored cover bytes for an owned book |

Shelf routes return `401 Unauthorized` when `CurrentUserProvider` cannot resolve the request.
They never fall back to a development identity. In development and tests, selecting a user still
posts to `/dev/users`, sets the alias cookie, and redirects through `/`; the root redirect then
takes the browser to `/reading`.

## Library search

Every shelf includes an ordinary GET search form. JavaScript disables its normal submit button until
the trimmed input has at least three letters, or is an ISBN prefix containing at least six digits;
the no-JavaScript fallback remains submit-capable and the server applies the same validation.
Spaces and hyphens are ignored for an otherwise numeric ISBN query. Invalid direct requests return
the search page with an explanatory validation message.

Search is owner-scoped and case-insensitive. Text matches only from the beginning of an effective
title or the beginning of an author word; ISBN searches match the beginning of the user's effective
ISBN (a private ISBN override replaces the canonical value). Results group non-empty shelves in To
Read, Reading, Finished order, retain each shelf's normal book order, and use the same book cards
and shelf navigation as ordinary shelves. A valid query with no matches says `No books found`.

`identity.api.CurrentUser` carries two values: the stable `UserId` used for every ownership and
authorization decision, and a normalized, nonblank display label intended for escaped UI output.
Identity adapters are responsible for supplying a non-sensitive label. The label is never used as
an ownership key.

## Book detail and covers

Shelf-card titles are ordinary HTML links to the detail page, so opening a book requires no
JavaScript. The owner-scoped detail projection loads the linked canonical edition and applies the
owner's effective metadata overrides, including ordered author overrides. It also returns the
private reading state and dates, private notes, and whether a cover is available. Provider names,
identifiers, origins, and other provenance are not part of this projection or the page.

Both routes first resolve the request through `CurrentUserProvider`; development and tests use the
selected-user cookie only inside that adapter. The browser never supplies an owner identifier.
Malformed UUIDs, unknown records, other users' records, and coverless books return the same
non-identifying `404`; an unresolved current user returns `401`.

The detail page preserves line breaks in escaped plain-text descriptions and private notes, shows
only dates valid for the current state, and provides ordinary shelf, state-change, and deletion
links. It uses the deterministic typographic placeholder when no stored cover is linked. A stored
cover is delivered separately as its original `image/*` MIME type with `Cache-Control: no-store`;
it is neither embedded in HTML nor fetched from a provider.

## Shelf projection and ordering

`Shelf` is the explicit mapping between route slug, heading, active navigation item, empty-state
copy, and persisted reading state. The jOOQ shelf adapter requires `CurrentUser` and `Shelf` for
every query. Its base row query and its ordered-author queries all constrain both `user_id` and
`state`; no unowned lookup or default owner exists.

Only the shelf projection leaves persistence: the owned user-edition ID, effective title, ordered
effective authors, validated reading state, and the user-edition creation instant. The state holds
the applicable start or finish date under the existing domain invariants. The ID is used only for
owner-scoped shelf-change and deletion links. A private title override replaces the canonical
title. When authors are overridden, the private sequence ordered by `position` replaces the
canonical sequence; otherwise canonical authors are ordered by their `position`. Canonical edition
IDs and user IDs remain internal.

Default ordering is:

| Shelf | Order |
| --- | --- |
| Reading | `started_on DESC, id ASC` |
| To Read | `created_at DESC, id ASC` |
| Finished | `finished_on DESC, id ASC` |

Every shelf uses the same checked Qute cover-card component and dedicated view model. Cards show a
written state and context line: Reading shows its start date, Finished shows its finish date, and
To Read shows an approximate, non-editable age derived from the internal creation instant. Dates
use the unambiguous English `d MMM uuuu` form.

The To Read age is computed once per request from an injected clock and
`rosies-books.default-zone`; the JVM ambient timezone is not used. Creation instants are converted
to calendar dates in that zone, then grouped with fixed floor-based thresholds: today, days below
one week, whole weeks below 30 days, approximate 30-day months below 365 days, and approximate
365-day years thereafter. A future timestamp caused by clock skew is displayed as added today.
These fixed inputs and boundaries make the deliberately approximate wording reproducible.

Finished uses the separate `findFinished(CurrentUser, Year selectedYear, Year currentYear)` query
contract. Its immutable projection contains the selected year, descending available years, and the
books for that year. Available years come only from the owner's records currently in Finished, with
the current year always included. The adapter filters by owner, Finished state, and the selected
year's inclusive/exclusive `LocalDate` range in PostgreSQL. A syntactically valid but unavailable
explicit year produces no projection and the web adapter returns `400 Bad Request`, just as it does
for a malformed year.

When `year` is omitted, the web adapter derives the current year from its injected clock and
`rosies-books.default-zone` after resolving the current user, then passes that year explicitly to
persistence. The JVM ambient timezone and browser timezone are not used, and no JavaScript is
required: descending year choices are ordinary `/finished?year=YYYY` links. The checked template
marks the selected year accessibly, reports a singular or plural annual count from the exact list
used to render cards, and keeps year navigation and manual book entry available for an empty year.
Reading and To Read continue through the generic shelf query and render no annual controls.

Timezone resolution deliberately remains outside persistence. A future user-timezone preference
can replace the configured `ZoneId` after user resolution without changing the URL contract, shelf
filtering, annual count, or stored date-only values.

## Typographic placeholders

Every shelf card currently uses a typographic placeholder. The effective title and
the exact ordered author list are hashed with Java's stable `String` and `List` hash contracts via
`Objects.hash`. The nonnegative remainder selects one of six checked-in theme class names. User
text is never emitted into a `style` attribute; it appears only as Qute-escaped text, while CSS
handles wrapping, clamping, and overflow.

Stored cover selection and delivery remain part of task 7-4. That work can replace the cover area
inside the shared card while retaining the deterministic placeholder fallback.

## Responsive shell

The shared stylesheet is mobile-first. Shelf navigation is an ordinary three-link bottom bar on
narrow viewports and becomes a sidebar at 48 rem. The layout includes visible keyboard focus,
safe-area padding, minimum-width protection, and text overflow handling. The manual-entry workflow
adds an ordinary link to each shelf header. The current cards retain ordinary Change shelf and
Delete links; book detail, quick finish, year filter, annual count, and layout toggle remain assigned
to their later tasks.

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

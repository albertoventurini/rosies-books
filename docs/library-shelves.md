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

Only the shelf projection leaves persistence: the effective title and the ordered effective
authors. A private title override replaces the canonical title. When authors are overridden, the
private sequence ordered by `position` replaces the canonical sequence; otherwise canonical
authors are ordered by their `position`. IDs are used internally for joining and deterministic
ordering but are not exposed to the web view model.

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
safe-area padding, minimum-width protection, and text overflow handling. Task 2-2 adds an ordinary
manual-entry link to each shelf header; book-detail, year-filter, count, layout-toggle, and shelf
mutation controls remain outside this task.

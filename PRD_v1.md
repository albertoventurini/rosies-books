# Liber Libri — Product Requirements Document

| Field | Value |
| --- | --- |
| Product | Liber Libri |
| Document status | Requirements complete; ready for implementation planning |
| Product type | Mobile-first progressive web application (PWA) |
| Initial audience | Two allowlisted users with fully private libraries |
| Primary purpose | Track books to read, books in progress, and completed reading history |

## 1. Product summary

Liber Libri is a deliberately small book-tracking application intended to replace a personal spreadsheet. It performs one task well: maintaining a private record of books a user wants to read, is reading, or has finished.

The application must be fast and comfortable on a mobile phone, particularly when installed or used through an iPhone browser. It will use server-rendered pages with progressive enhancement where useful. It is not a social network, recommendation service, reading challenge, review platform, or general-purpose personal knowledge system.

Two people will use the initial deployment. Each person has a separate account and a completely isolated library. Neither user can discover, inspect, or infer the other user's library or activity through the application.

## 2. Product principles

1. **Less is more.** Every feature must directly support tracking books or maintaining accurate reading history.
2. **Mobile first.** Primary actions must be easy to complete with one hand on a phone.
3. **Private by default.** Libraries, notes, metadata overrides, and reading activity belong only to the authenticated user.
4. **Reliable ownership of data.** Once an edition is added, the user's library must not depend on an external book provider remaining available.
5. **Preserve history.** Moving a book between sections or rereading it must not destroy valid prior reading records.
6. **Prefer direct interactions.** Common actions should require few screens, fields, or confirmations.

## 3. Goals

- Let an allowlisted user sign in quickly with Google.
- Show the user's library in three clear sections: In Progress, To Read, and Finished.
- Find editions using partial title or author searches against an external book provider.
- Capture an edition manually when search has no suitable result.
- Store detailed edition metadata and a durable local copy of its cover.
- Let a user freely correct metadata without changing the shared canonical edition.
- Track multiple readings of the same edition, including rereads.
- Show how many readings the user completed in a selected year.
- Let the user search their own library by title or author.
- Provide both cover-card and compact-list layouts and remember the user's preference.
- Keep the data model simple while separating shared edition identity from private user data.

## 4. Explicit non-goals

The following are intentionally outside the product, not merely deferred MVP items:

- Social features, following, sharing, public profiles, or visibility into another user's library
- Ratings, reviews, reading challenges, annual goals, targets, or streaks
- Recommendations or discovery feeds
- Reminders, notifications, or push messaging
- Reading progress by page, percentage, or chapter
- Tags, collections, shelves beyond the three defined sections, or custom organization systems
- Account profile customization
- An "Abandoned" reading status
- Offline library access or offline mutations

The following are also excluded from the current product:

- Spreadsheet migration, bulk import, or initial-data transfer
- Export or backup downloads exposed through the user interface
- An administration screen for users or the email allowlist
- Multiple authentication providers
- User-uploaded or user-replaced cover images
- Aggregate edition statistics or analytics screens

The data model may permit future edition-level statistics, but the application must not expose cross-user activity or introduce statistics that compromise library privacy.

## 5. Users and access

### 5.1 User profile

The initial deployment has two users. Each user has:

- A Google-authenticated identity
- A private library
- Private edition metadata overrides
- Private notes
- Private reading and status history
- A saved layout preference

There are no roles and no administrator interface.

### 5.2 Authentication

- Authentication uses Google OpenID Connect (OIDC).
- Login must use the standard Google sign-in experience and work reliably in mobile browsers and installed PWA mode.
- A successful provider login is accepted only when Google reports a verified email address present in a deployment-configured allowlist.
- The allowlist is configured outside the application UI, such as through deployment configuration or environment-backed configuration.
- The durable account identity must be keyed by the provider issuer and subject identifier, not by email address alone.
- A non-allowlisted user receives a clear access-denied page and no local account or library access.
- Sessions should persist across normal browser restarts while using secure, revocable server-side authentication state.
- Logout ends the current session and returns the user to the login page.

### 5.3 Account deletion

An authenticated user can permanently delete their account from a minimal settings page.

- The action requires an explicit confirmation explaining that it cannot be undone.
- Deletion removes the account, sessions, user-edition links, metadata overrides, notes, reading records, history, and preferences.
- Shared canonical editions and covers referenced by another user are not deleted.
- Canonical editions and covers with no remaining user references may be removed as orphaned data.
- All active sessions for the deleted account are invalidated.
- There is no recovery or restore flow in the product.

## 6. Domain terminology

### 6.1 Edition

A shared canonical description of a specific published edition or format of a book. Different formats or publications may be distinct editions. It contains provider or manually supplied bibliographic metadata and the application's stored copy of a cover.

An edition uses an internal generated ID as its primary identity. ISBN-13, when present, is normalized and uniquely constrained but is not the primary key because some editions do not have an ISBN-13.

### 6.2 User edition

The private link between a user and an edition. It represents that edition in one user's library and contains:

- User-specific metadata overrides
- Private notes
- Current library section
- The user's reading records for the edition
- Internal creation and update timestamps

There may be only one user-edition link for a given user and canonical edition.

### 6.3 Reading

One occurrence of the user reading an edition. An edition can have multiple readings for the same user. A reread creates a new reading rather than overwriting an earlier completed reading. A new To Read item may exist before any Reading is created.

A reading contains:

- Start date, required while In Progress but optional for historical Finished entries
- Finish date, required while Finished
- An association with the relevant section-change history

### 6.4 Canonical metadata and user overrides

Canonical metadata belongs to the shared Edition. A user edit is stored on UserEdition as a private overlay and never mutates the canonical Edition.

For each overridable field, the effective value displayed to the user is:

1. The user's override, including an explicitly blank optional value; otherwise
2. The canonical edition value.

The persistence representation must distinguish "not overridden" from "overridden with an empty value."

## 7. Information architecture

### 7.1 Primary navigation

The authenticated application has three primary tabs:

1. **In Progress**
2. **To Read**
3. **Finished**

The active tab must be visually and programmatically identifiable. On mobile, navigation remains reachable without horizontal page scrolling and respects device safe areas.

A prominent Add Book action is available from every primary tab. Library search and the layout toggle are also readily accessible.

### 7.2 Book placement

- A user edition appears as one card or list row in exactly one primary section at a time.
- Its current section is independent of how many earlier readings it has.
- When a previously finished edition is being reread, its single library card appears in In Progress. Earlier finished readings remain visible in the edition detail and continue to count toward the relevant annual totals.

### 7.3 Layout modes

The user can switch between:

- **Cover cards:** cover-focused layout with title, author, and context-relevant date information
- **Compact list:** denser rows with a small cover or placeholder, title, author, status, and context-relevant date information

One server-side preference is remembered per user and applied consistently across all three sections and future sessions.

## 8. Functional requirements

### 8.1 View In Progress

The section shows all editions currently being read.

- There is no year filter.
- Default ordering is most recently started first.
- Every in-progress reading has a start date.
- The item exposes a quick action to mark the reading Finished.
- The item can be opened to view details, edit metadata or notes, change section, or delete data.
- An in-progress edition can be moved back to To Read without creating an Abandoned state. The same reading and its original start date are preserved so it can later be resumed.

### 8.2 View To Read

The section shows editions the user intends to read but is not actively reading.

- There is no year filter.
- Default ordering is most recently added to the user's library first, using an internal timestamp rather than a user-editable Date Added field.
- A newly added To Read edition does not require a start or finish date.
- Moving an item to In Progress requires a start date and defaults it to the user's current local date.
- If the item represents a paused reading moved back from In Progress, resuming it retains the original start date by default.
- An item may be marked directly as Finished. Finish date is required; start date may remain unknown.

### 8.3 View Finished

The section shows user editions whose current section is Finished.

- A year filter is available only in this section.
- The default selected year is the current year according to the user's browser-local date.
- Available years are derived from the user's completed readings, with the current year always available.
- A card is included when the edition is currently in Finished and has at least one reading completed in the selected year.
- Default ordering is by the most recent matching finish date, newest first.
- If an edition was completed more than once in the selected year, it remains one card and indicates the number of matching readings or exposes those readings in its details.
- Empty years show a concise empty state and retain the ability to change the year or add a book.

### 8.4 Annual completed-reading count

- The Finished view prominently displays the number of readings completed in the selected year, for example, "12 books read in 2026."
- The number counts completed Reading records, not distinct editions. Finishing the same edition twice in one year counts twice.
- There is no goal, target, progress bar, percentage, streak, or comparison with another year or user.
- The count includes all readings completed in the selected year even if an edition's card currently appears in In Progress because it is being read again.
- As a consequence, the annual count is not required to equal the number of cards currently visible in the Finished section.

### 8.5 Search the user's library

- An authenticated user can search only their own library by partial title or author text.
- Search uses the effective user-specific metadata, including overrides.
- Matching is case-insensitive and tolerant of partial terms.
- Results may span all three sections and clearly show each edition's current section.
- Search never returns another user's user-edition, notes, readings, overrides, or activity.
- A no-results state offers the Add Book action but does not automatically send a private-library query to the external provider.

### 8.6 Add a book through provider search

The Add Book flow is distinct from private-library search.

1. The user enters a partial title, author name, or combination.
2. The application queries an abstract external book provider.
3. Results show enough edition-level detail to distinguish publications, including cover when available.
4. The user selects one result and can review its full normalized metadata.
5. The application detects whether the canonical edition is already linked to the user's library.
6. If already linked, it opens the existing user edition instead of creating a duplicate.
7. Otherwise, the user chooses To Read, In Progress, or Finished and confirms the addition.

Date rules during addition:

- **To Read:** no date is required.
- **In Progress:** start date is required and defaults to today.
- **Finished:** finish date is required and defaults to today; start date is optional.

The user can edit the dates before saving. Date values are date-only values and must not shift because of server timezone conversion.

### 8.7 External book-provider abstraction

No specific provider is mandated by this PRD. The adapter contract must support:

- Partial title search
- Partial author search
- Search-result pagination or bounded result loading
- Provider edition identifiers
- Title, subtitle, and multiple authors
- ISBN-10 and ISBN-13 when available
- Publisher and publication date
- Edition or format
- Page count
- Language
- Description
- Cover retrieval
- Clear handling of missing fields, rate limiting, timeouts, and provider errors

Provider selection will be made during technical planning based on metadata quality, edition specificity, cover availability, licensing, reliability, and operating limits.

Provider search is an enhancement to data entry, not a runtime dependency for an existing library. When an edition is selected, normalized metadata and the cover are copied into the application's database. Canonical data must not be overwritten automatically in a way that unexpectedly changes an existing user's effective metadata; later provider data may fill missing canonical fields under a controlled rule.

If provider search is unavailable or returns no suitable result, the user can proceed directly to manual entry.

### 8.8 Edition identity and duplicate handling

- Edition uses an internal ID as the primary key.
- A valid ISBN-13 is normalized before comparison and has a unique constraint when present.
- ISBN-10 should be converted or linked to its ISBN-13 equivalent when conversion is valid.
- When ISBN-13 is absent, the combination of provider and provider edition ID is uniquely constrained when available.
- Manually entered editions without reliable identifiers receive an internal ID. The application must not automatically merge them based only on similar title and author text.
- Within a user's library, `(user_id, edition_id)` is unique.
- If an add operation resolves to an edition already present in that user's library, the existing user edition opens instead of being duplicated.
- Metadata overridden by a user does not change canonical identity or global deduplication keys.

### 8.9 Manual edition creation

The manual-entry path is available from Add Book and from provider empty/error states.

- Title and at least one author are required.
- Additional authors can be added and ordered.
- All other bibliographic fields are optional.
- A manually created edition without a cover uses the application's local placeholder.
- Users cannot upload a cover or supply an arbitrary remote image URL.
- Normal validation applies to ISBNs, dates, and numeric fields when supplied.
- Saving creates the canonical edition if needed, the private user-edition link, and the selected initial state in one atomic operation.

### 8.10 Edition metadata

The application supports these edition fields:

- Title — required
- Subtitle — optional
- Authors — one or more required
- Edition or format — optional
- ISBN-10 — optional
- ISBN-13 — optional
- Publisher — optional
- Publication date — optional and may be less precise than a full date
- Page count — optional
- Language — optional
- Description — optional
- Stored cover — optional, with local placeholder fallback

The user can edit bibliographic text and values in their own library, including values originally supplied by a provider. Edits are private UserEdition overrides and do not modify the canonical Edition or another user's view.

Title and at least one author must remain present in the user's effective metadata. A user-visible ISBN override does not silently change the canonical edition's identity.

### 8.11 Cover persistence

- Provider cover images are downloaded and stored in the database when an edition is accepted into the library.
- The cover record stores binary bytes rather than base64 text, MIME type, pixel dimensions, a content hash, original source URL for provenance, and fetch timestamp.
- Downloaded content is validated as a supported raster image and subject to configured byte and dimension limits.
- The stored copy is served for subsequent library views; pages must not hotlink to the provider.
- A provider or cover-download failure does not prevent saving the book. A local placeholder is shown instead.
- Cover loading is responsive and lazy where appropriate to avoid delaying mobile page rendering.
- Users cannot upload, replace, crop, or edit covers.

### 8.12 View and edit an edition

The edition detail view shows:

- Effective bibliographic metadata
- Stored cover or placeholder
- Current section
- Private notes
- Current and previous readings with relevant dates
- Retained status history
- Actions appropriate to the current section

The user can:

- Edit supported metadata as private overrides
- Edit private notes as plain text
- Change the current section
- Edit start and finish dates subject to state validation
- Mark the current reading Finished
- Reopen a mistakenly finished reading
- Start a new reread
- Delete an individual reading
- Delete the edition from their library

No rich-text editor, rating control, sharing action, or social metadata is present.

### 8.13 State transitions

All section changes retain a timestamped history event. The affected reading is associated with the event when applicable.

#### To Read → In Progress

- If no paused reading exists, create a new reading.
- Start date is required and defaults to today.
- If resuming the same reading after it was moved back to To Read, retain its original start date by default.

#### In Progress → To Read

- Preserve the current reading and original start date.
- Do not label it Abandoned and do not create a new reading.
- Resuming later continues the same reading.

#### In Progress → Finished

- Complete the same reading.
- Finish date is required and defaults to today.
- The existing start date is retained.

#### To Read → Finished

- Finish date is required and defaults to today.
- Start date is optional.
- Complete the preserved reading if one exists; otherwise create a finished reading.

#### Finished → In Progress: correction

- A Reopen action reopens the most recent finished reading rather than creating a new one.
- Its finish date is cleared from the active record, removing that completion from annual counts.
- Its prior status transition remains in history so the correction is auditable.
- Start date must be present while it is In Progress; if the historical reading had no start date, the user supplies one, defaulted to today.

#### Finished → Read Again

- Read Again is distinct from Reopen.
- It preserves every earlier completed reading.
- It creates a new current reading and sets the user edition to In Progress.
- Start date is required and defaults to today.
- The edition's single card moves to In Progress.

#### Finished → To Read

- This represents an intention to read the edition again later.
- Existing completed readings are preserved.
- No new reading is created until the user starts it.

### 8.14 Delete an individual reading

- The action requires confirmation and identifies the reading by its dates and completion state.
- Only the selected reading and its reading-specific history are permanently deleted.
- Other readings, edition metadata overrides, and notes remain.
- If a current reading is deleted and completed readings remain, the edition becomes Finished using its remaining history.
- If no readings remain, the user edition remains in To Read.
- Annual counts update immediately after deletion.

### 8.15 Delete a book from the library

- The action requires confirmation that all private data for this edition will be removed.
- It permanently deletes the UserEdition, all of that user's readings and status history for it, notes, and metadata overrides.
- It does not delete another user's link or private data for the same canonical Edition.
- The shared Edition and cover may be removed only when no user references remain.

## 9. Empty, loading, and error states

- Each empty primary section explains its purpose in one short sentence and offers Add Book.
- Provider search displays a clear loading state without blocking the rest of the library.
- Provider timeout, rate-limit, and unavailable states preserve the query where possible and offer retry and manual entry.
- A failed cover fetch falls back to the local placeholder without failing the edition save.
- Validation errors appear adjacent to the relevant field and preserve user input.
- An unauthorized or cross-user resource request returns no private resource data.
- When connectivity is unavailable, the application presents an online-required message. It does not imply that edits were saved offline.
- Destructive-operation failures leave the existing record intact and provide a retryable error.

## 10. PWA and responsive experience

- The application includes a web app manifest, application icons, theme metadata, and standalone display support.
- It is served exclusively over HTTPS outside local development.
- Installation is optional; the complete experience remains available in a normal browser tab.
- The layout is designed first for narrow phone screens and scales cleanly to tablet and desktop widths.
- Primary touch targets are comfortably sized and separated.
- Forms use suitable mobile input types and avoid unnecessary typing.
- The UI respects iPhone safe areas and remains usable with browser text enlargement.
- Google authentication redirects must return the user to a valid authenticated application route in both browser-tab and installed modes.
- No user data is promised offline. If a service worker is used for installability or static assets, it must not cache authenticated library pages or mutations as an offline data store.
- Navigation, forms, dialogs, errors, and state changes must be usable by keyboard and understandable with common screen readers.

## 11. Data model

The names below are conceptual and may be adjusted to local code conventions. The relationships and constraints are requirements.

### 11.1 User

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `oidc_issuer` | Google issuer |
| `oidc_subject` | Stable provider subject |
| `email` | Verified normalized email used for allowlist evaluation and display where needed |
| `created_at`, `updated_at` | Internal timestamps |

Constraints:

- Unique `(oidc_issuer, oidc_subject)`
- Email allowlist is deployment configuration, not a database-managed admin feature

### 11.2 UserPreference

| Field | Notes |
| --- | --- |
| `user_id` | One-to-one with User |
| `library_layout` | `COVER_CARD` or `COMPACT_LIST` |

### 11.3 Edition

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `isbn13` | Nullable normalized canonical identifier |
| `isbn10` | Nullable normalized identifier |
| `provider_name` | Nullable source provider |
| `provider_edition_id` | Nullable provider identity |
| Bibliographic fields | Canonical title, subtitle, authors, format, publisher, publication date, page count, language, description |
| `cover_asset_id` | Nullable reference to stored cover |
| `metadata_origin` | Provider or manual provenance |
| `created_at`, `updated_at` | Internal timestamps |

Constraints:

- Unique non-null `isbn13`
- Unique non-null `(provider_name, provider_edition_id)`
- Canonical title and at least one canonical author are required when creating a usable edition

Authors may be represented through ordered child rows rather than a single delimited string.

### 11.4 CoverAsset

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `content` | Binary image bytes |
| `mime_type` | Validated supported image type |
| `width`, `height` | Validated pixel dimensions |
| `content_hash` | Integrity and optional deduplication |
| `source_url` | Provenance only; never used for normal rendering |
| `fetched_at` | Retrieval timestamp |

### 11.5 UserEdition

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `user_id` | Owner; mandatory scope for every access |
| `edition_id` | Shared canonical edition |
| `current_section` | `TO_READ`, `IN_PROGRESS`, or `FINISHED` |
| `current_reading_id` | Nullable reference to the one unfinished/resumable Reading |
| Metadata overrides | Private field overlay with explicit-empty support |
| `private_notes` | Optional plain text |
| `created_at`, `updated_at` | Used internally, including To Read ordering |

Constraints:

- Unique `(user_id, edition_id)`
- Effective title and at least one effective author must always be present

### 11.6 Reading

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `user_edition_id` | Parent private library entry |
| `started_on` | Date-only; nullable except for In Progress |
| `finished_on` | Date-only; required for a completed reading |
| `created_at`, `updated_at` | Internal timestamps |

Business constraints:

- At most one unfinished/resumable reading may exist for a UserEdition, and UserEdition points to it through `current_reading_id`.
- An In Progress UserEdition requires a referenced current reading with `started_on` and without `finished_on`.
- A To Read UserEdition may reference an unfinished reading when it was moved back from In Progress; otherwise its current reading is null.
- A Finished UserEdition has no unfinished reading.
- A completed reading requires `finished_on`; `started_on` may be null.
- If both dates exist, finish date cannot precede start date.

### 11.7 StatusHistory

| Field | Notes |
| --- | --- |
| `id` | Internal primary key |
| `user_edition_id` | Owning private library entry |
| `reading_id` | Nullable affected reading |
| `from_section`, `to_section` | Transition values |
| `changed_at` | Timestamp |

History rows are private and cascade with their owning user edition or reading.

### 11.8 Relationship summary

```text
User 1 ─── 1 UserPreference
User 1 ─── * UserEdition * ─── 1 Edition 1 ─── 0..1 CoverAsset
UserEdition 1 ─── * Reading
UserEdition 1 ─── * StatusHistory
Reading 1 ─── * StatusHistory (optional association)
```

## 12. Privacy and security requirements

- Every library query and mutation is scoped by the authenticated user's ID on the server; client-supplied record IDs are never sufficient authorization.
- Users cannot enumerate another user's UserEdition, Reading, StatusHistory, notes, preferences, or account details.
- Shared Edition lookup must not reveal which other users reference an edition.
- Cross-user aggregate activity is not exposed.
- Authentication cookies are secure, HTTP-only, and use an appropriate same-site policy.
- State-changing requests have cross-site request forgery protection.
- Provider text and user-entered metadata are escaped on output; descriptions are not rendered as trusted provider HTML.
- Cover downloads enforce timeouts, response-size limits, supported content types, and image validation. The server must not expose a general-purpose arbitrary URL fetcher.
- Secrets, OIDC credentials, provider credentials, and the allowlist are supplied through deployment configuration and are not committed to source control.
- Sensitive note content and provider tokens are not written to normal application logs.
- Account and library deletions are transactional and auditable without retaining the deleted private content in application-facing storage.
- Production data must have an operational backup strategy, while no backup-management UI is required in the product.

## 13. Technical direction and constraints

These are implementation directions rather than irreversible product requirements:

- Java with Quarkus is the preferred backend candidate.
- Server-side rendering is preferred, with JTE as the template-engine candidate.
- JavaScript should be limited to progressive enhancements such as dialogs, layout switching, and responsive interactions that materially improve usability.
- A relational database is used for transactional data, metadata overlays, reading history, preferences, and binary cover storage.
- Schema changes are managed through versioned migrations.
- Authentication uses Quarkus-compatible OIDC support and server-controlled authorization.
- External search is accessed through a provider-neutral application interface so providers can be evaluated or replaced without changing the product flow.
- Cover images are served from application-controlled storage with appropriate cache headers and responsive sizing.
- The UI must remain functionally correct when progressive enhancements fail, except where the authentication provider itself requires browser scripting.

A short technical spike should validate JTE integration, OIDC behavior in iPhone standalone mode, the chosen database's binary-cover behavior, and at least one provider adapter before implementation is committed.

## 14. Non-functional requirements

### 14.1 Performance

- Normal server-rendered library navigation should feel immediate on a typical mobile connection.
- Database-backed library pages should target a 500 ms p95 server response time under the expected two-user load, excluding external provider search.
- Provider calls use bounded timeouts and do not block access to existing library data.
- Covers are resized or served responsively, cached, and lazy-loaded outside the initial viewport.
- Common queries for current section, effective title/author search, and finish year are indexed appropriately.

### 14.2 Reliability and data integrity

- Edition creation, user-edition creation, and initial reading state are atomic.
- State transitions and their history events are committed atomically.
- Annual counts are derived from completed readings rather than a separately maintained counter.
- Retrying an add request must not create duplicate user-edition links or duplicate readings.
- Provider unavailability must not prevent viewing, searching, editing, or deleting an existing library.
- Date-only reading values are not converted through a server timezone in a way that changes the selected calendar date.

### 14.3 Compatibility and accessibility

- The primary supported experience is a current mobile Safari or Chromium-based mobile browser, with functional support for current mainstream desktop browsers.
- The application works at narrow phone widths without horizontal page scrolling.
- Interactive controls have clear labels, visible focus, adequate contrast, and comfortable touch targets.
- Status and validation are not communicated by color alone.
- Forms, navigation, confirmation dialogs, and book actions support keyboard and assistive-technology use.

### 14.4 Observability

- Log authentication outcome categories, provider failures, validation failures, and unexpected server errors without logging private notes or unnecessary book-query content.
- Provide health checks suitable for deployment monitoring.
- Track provider latency and error rate separately from application/database latency.
- Product analytics and user-behavior tracking are not required.

## 15. Acceptance criteria

The initial release is acceptable when all the following are demonstrably true:

1. An allowlisted Google user can sign in on a mobile browser and reach only their own library.
2. A non-allowlisted Google user cannot create or access a library.
3. Each primary section displays the correct user editions with the agreed default ordering.
4. The Finished section defaults to the current year and accurately counts completed readings in that year.
5. No year filter appears in In Progress or To Read.
6. Provider search accepts partial title and author input and returns distinguishable edition results when the provider has matches.
7. Selecting a provider result copies its normalized metadata and available cover into the database.
8. Existing library pages and covers continue to work without the provider being available.
9. A no-match or provider-error flow allows manual creation with title and at least one author.
10. Adding an edition already in the current user's library opens the existing user edition and creates no duplicate.
11. The same canonical edition can be linked independently to both users without exposing either user's private data to the other.
12. One user's metadata overrides and notes never change the other user's effective metadata.
13. An in-progress reading requires a start date; a finished reading requires a finish date; a historical finished reading may omit its start date.
14. Marking In Progress as Finished completes the same reading and updates the selected year's count.
15. Reopen changes the most recent completion back to the same in-progress reading and removes that completion from the annual count.
16. Read Again preserves earlier readings, creates a new in-progress reading, and moves the single edition card to In Progress.
17. Moving In Progress back to To Read preserves the reading and its start date without creating an Abandoned status.
18. A user can search their own effective titles and authors without receiving the other user's data.
19. The cover-card/compact-list choice persists server-side across logout and later login.
20. Deleting a reading updates history and annual counts without deleting the user edition.
21. Deleting a user edition removes all of that user's associated private data but not another user's link to the canonical edition.
22. Deleting an account permanently removes that user's library, readings, history, notes, overrides, preferences, and sessions.
23. The complete application remains usable on a narrow mobile viewport and can be installed as a PWA without claiming offline data support.

## 16. Release boundary

The first release includes only the capabilities specified in this document. In particular, implementation should not add ratings, goals, tags, recommendations, social behavior, notifications, import workflows, cover uploads, or extra reading states in anticipation of possible future use.

Provider selection, concrete framework versions, hosting, database product, deployment topology, and detailed visual design are implementation-planning decisions. They must preserve the behavior, privacy boundaries, and simplicity defined here.

# Rosie's books — Implementation Milestones

| Field | Value |
| --- | --- |
| Product | Rosie's books |
| Source requirements | `PRD.md` |
| Source design | `mockups/Liber Libri.dc.html` |
| Engineering conventions | `ENGINEERING_CONVENTIONS.md` |
| Document status | Draft for review |
| Release boundary | Docker Compose service and operating documentation; deployment to a host is not included |

## 1. Planning decisions

- The application will use Java, Quarkus, Qute, jOOQ, and PostgreSQL.
- Persistence will use imperative JDBC and generated jOOQ schema types, not Hibernate ORM or handwritten SQL as the ordinary query mechanism.
- Server-rendered views will use Qute checked/type-safe templates with build-time validation.
- The production artifact will be the Quarkus JVM fast-jar packaged in a Docker image. Native-image compilation is outside this plan. Startup and memory measurements are required only if a concrete deployment constraint or native-image proposal creates a decision that needs that evidence.
- Pages will be server-rendered. JavaScript will be limited to progressive enhancement where it makes an interaction materially better.
- Schema changes will use versioned migrations.
- The application will be packaged as a Docker image and delivered with a Docker Compose configuration.
- Authentication is deliberately late in the sequence so the core library can be built first.
- Ownership and privacy are not postponed with authentication. The data model and every repository/service boundary will be user-scoped from the beginning.
- Development-only seeded identities will provide the current user until authentication is implemented. They must be unavailable in the production profile.
- The OIDC provider will be selected during the authentication milestone. No early Google-specific spike will be performed. If the selected provider changes the requirements currently stated in `PRD.md`, the PRD will be updated before authentication work begins.
- Responsive PWA and resilience work will be completed before external-provider integration so the provider-independent application is hardened first.
- Book-provider selection will be an explicit research spike immediately before provider integration, after the manual library and responsive PWA work end to end.
- Accessibility-specific implementation and review are deferred from this plan, including the related clauses currently present in `PRD.md`; those clauses are not release criteria for these milestones. This does not relax the requirements for responsive layouts, clear browser interactions, or no-JavaScript operation.
- Milestones may be infrastructure-focused when that reduces later implementation risk; each milestone still has objective exit criteria.

The mockup's open product questions are resolved as follows:

- Confirm before moving a book to **To Read** when the move will clear recorded dates.
- Let users reset a private metadata override to the canonical value.
- Let users add a previously unknown start date to a Finished book.
- Pre-fill the manual-entry title with the provider-search query when practical.
- Show an approximate, non-editable “added” age for books in To Read.

## 2. Sequencing principles

The sequence establishes invariants before adding breadth:

1. Make the chosen stack build, render, migrate, and test reliably.
2. Establish user ownership and domain rules before exposing library operations.
3. Complete the provider-independent manual workflow before relying on external data.
4. Build the primary shelf experience, then the less frequent editing and search flows.
5. Make the provider-independent application responsive, installable, and resilient.
6. Add the external provider and durable covers behind a stable application interface.
7. Harden the complete core experience before replacing development identities with OIDC.
8. Finish with release configuration, documentation, and full acceptance verification.

No milestone should introduce ratings, goals, tags, recommendations, social behavior, notifications, imports, cover uploads, extra reading states, or other features outside the PRD.

## 3. Milestones

### Task execution contract

Each numbered task below is a substantial, coherent unit assigned to one agent. An agent starting with a new context window must be able to understand the task from its entry, `PRD.md`, `ENGINEERING_CONVENTIONS.md`, and the repository at the task's milestone baseline; no prior conversation is required. Milestones are sequential. Within a milestone, tasks may rely on earlier numbered tasks where the dependency is evident from the delivered application flow.

For every task, the assigned agent must:

- implement only the named production behavior and supporting repository changes;
- add or update the tests named in the task and run the smallest relevant test suite;
- update affected developer or operator documentation and shared contracts in the same change; and
- hand off a short completion note listing changed behavior, tests run, and any unresolved blocker.

A task should normally deliver a usable vertical slice or a complete technical foundation, not a single test category, edge case, or minor UI element. The nested bullets under a task are its scope and completion criteria, not additional tasks or separately assignable work. Its tests, error handling, security checks, and documentation remain part of the parent task. Exploratory work is complete only when its named decision record or contract has been committed.

### Milestone 0 — Application foundation

**Outcome:** A reproducible Quarkus JVM application can render a type-safe Qute page, connect to PostgreSQL, apply a migration, and run its automated tests.

Tasks:

- **0-1 — Scaffold and pin the application.**
  - Select compatible Java, Quarkus, Qute, jOOQ, PostgreSQL, migration-tool, build-tool, and test-tool versions.
  - Record every selected version, the reason for non-obvious choices, and the repository locations used to upgrade it.
  - Scaffold feature-first top-level packages for the initial business capabilities, colocating their web, use-case, domain, and persistence code rather than creating top-level technical-layer packages.
  - Define each feature's small exposed API and allowed feature dependencies, and add ArchUnit checks proving internal-package encapsulation, infrastructure confinement, and an acyclic feature graph.
- **0-2 — Establish the web foundation.**
  - Integrate Qute and render one minimal route through the shared server-side application shell in development/test and packaged JVM modes.
  - Use a checked/type-safe template and prove invalid template expressions or parameter contracts fail the build.
  - Add the typography, color, spacing, and baseline static-asset pipeline derived from the mockup without implementing a full product screen.
  - Add distinct startup/liveness/readiness behavior and a shared structured unexpected-error mapping/logging foundation.
  - Test page and static-resource responses, healthy and database-unready states, and handled errors without leaked stack traces or private input.
- **0-3 — Establish PostgreSQL, migrations, and persistence testing.**
  - Add a health-checked development Docker Compose PostgreSQL service with explicit application/test configuration and documented start, stop, and data-reset behavior.
  - Configure versioned migrations and verify they apply in order to a clean database and cause a detectable startup failure when migration fails.
  - Generate jOOQ schema types reproducibly from the versioned migrations, integrate generated sources into compilation, and fail the build when generated types and the migrated schema disagree.
  - Configure jOOQ against the Quarkus-managed JDBC datasource and prove application transaction participation and rollback behavior.
  - Implement the chosen per-run isolated PostgreSQL integration-test setup, including its cleanup model and one repository round trip.
  - Prove binary cover data and MIME metadata can be written/read at the intended maximum size, reject data above the limit, and record the measured limit for milestone 7.
  - Expose one documented command that runs unit and PostgreSQL-backed integration tests, builds the Quarkus JVM fast-jar, and exercises the packaged application without development-only IDE behavior.

Exit criteria:

- A new checkout can be started from documented commands.
- A checked/type-safe Qute page renders successfully against a migrated PostgreSQL database.
- Unit and PostgreSQL-backed integration tests run through one documented command.
- The packaged application starts without relying on development-only IDE behavior.
- Before closing the milestone, validate the documented build, test, database, development-mode, and packaged-startup commands from a clean checkout or equivalent source-only working tree.

### Milestone 1 — Domain model, persistence, and ownership boundary

**Outcome:** The complete core data model and state rules exist behind APIs that always require an owning user.

Tasks:

- **1-1 — Implement the core ownership schema.**
  - Add forward migrations, generated jOOQ schema types, and persistence adapters for User, UserPreference, Edition, ordered Edition authors, UserEdition, and private metadata overrides.
  - Define foreign-key ownership and deletion behavior explicitly.
  - Preserve unknown, year-only, year-month, and full publication dates without inventing missing components; test round trips, comparisons, and invalid components.
  - Store normalized ISBN-13 and provider/edition identifiers with canonical uniqueness rules and deterministic constraint-to-domain error mapping.
  - Enforce unique `(user_id, edition_id)` links while allowing two users to link independently to the same Edition.
  - Add indexes for user/state/date shelf ordering and the planned effective-title/author search representation; verify their presence and representative query plans.
- **1-2 — Implement metadata normalization and overrides.**
  - Normalize ISBN separators and validate check digits, with valid ISBN-10-to-ISBN-13 conversion and canonical linking behavior.
  - Model “not overridden,” “overridden with a value,” and “overridden with an explicit blank” distinctly for every supported field, including ordered authors.
  - Resolve effective metadata from canonical Edition values plus one user's overrides without allowing an empty effective title or author list.
  - Test published valid/invalid ISBN examples, normalization properties, all override states, ordered-author persistence, explicit blanks, and field-by-field resolution.
- **1-3 — Implement reading-state rules.**
  - Model Reading, To Read, and Finished with every permitted start/finish-date combination and prevent invalid construction or persistence.
  - Implement every allowed source/target transition with explicit set, retain, and clear behavior for dates.
  - Signal when confirmation is required because moving to To Read will discard dates.
  - Verify the complete invariant and transition matrices, including transactional rollback on invalid changes.
- **1-4 — Establish the user ownership boundary.**
  - Seed at least two distinct users only in development/test profiles and provide a development-only current-user selector.
  - Require a current-user value at every UserEdition repository and application-service entry point.
  - Query and mutate owned records using both user and record identity; never authorize by UserEdition ID alone.
  - Add profile/startup and architecture/API tests proving the seeds and selector are unavailable and fail closed in production and that ID-only public methods cannot be introduced.
- **1-5 — Verify persistence integrity and isolation.**
  - Build a reusable two-user PostgreSQL fixture.
  - Cover invalid dates, duplicate user links, concurrent linking, duplicate/conflicting canonical identifiers, override semantics, state transitions, and application error mapping.
  - Verify indexed shelf ordering with records tied on primary sort fields.
  - Exercise every public core read/write service against another user's UserEdition and assert denial/not-found behavior without mutation or identifying leakage.
  - Assert every constraint or transaction failure leaves no partial write.

Exit criteria:

- The domain layer cannot create an invalid state/date combination.
- The same canonical edition can belong independently to both seeded users.
- Neither seeded user can read or mutate the other's UserEdition through public application interfaces.
- Effective metadata and explicit-empty overrides behave as specified.

### Milestone 2 — Manual-entry library vertical slice

**Outcome:** A seeded user can create a book without any external provider, assign its initial state, view it, change its state, and remove it.

Tasks:

- **2-1 — Build the library shell and shelves.**
  - Build the shared user-scoped shell through the development identity boundary and show only non-sensitive identity context.
  - Add stable mobile-first routes and active navigation for Reading, To Read, and Finished using ordinary links.
  - Implement user-scoped shelf queries with the PRD's default ordering and initial server-rendered lists/empty states.
  - Add a deterministic typographic cover placeholder based on effective title/author data for every book without a stored cover.
  - Verify all routes receive the selected user, ordering and isolation are correct, long/missing/escaped text renders safely, and navigation works without JavaScript.
- **2-2 — Implement manual book entry.**
  - Build the server-rendered manual form with required title and ordered authors plus every optional Edition field supported by the PRD.
  - Bind repeated authors in order and allow selection of every initial state with only its permitted dates.
  - Validate ISBN, page count, partial publication date, required effective title/authors, supported field limits, and date relationships at the request boundary through the domain rules.
  - Re-render every submitted value and all field errors after failure.
  - Test successful binding and every initial state, individual validation rule, combined errors, missing optionals, and escaped input.
- **2-3 — Persist manual additions atomically.**
  - Resolve a reliable normalized identifier to an existing canonical Edition or create a new manual Edition.
  - Create the current user's UserEdition in the same transaction without fuzzy matching.
  - Handle repeated and concurrent submissions deterministically without duplicating the user link.
  - Test successful reuse/new creation and rollback at every write boundary so no canonical or private partial data remains.
- **2-4 — Implement shelf state changes.**
  - Add user-scoped POST actions for every state transition, collecting the required new dates and reusing the domain transition service.
  - Present the transition's exact date consequences and require a second confirmed request before a To Read move clears recorded dates.
  - Provide clear success, validation, cancellation, not-found, conflict, and transactional-failure responses.
  - Test successful transitions, missing confirmation, cancellation, invalid dates, stale/repeated submissions, persistence rollback, and cross-user attempts.
- **2-5 — Implement deletion and verify the vertical slice.**
  - Add a confirmation page and user-scoped deletion POST that removes only the current user's UserEdition and private data transactionally.
  - Remove an unreferenced manual canonical Edition only when safe, preserving an Edition referenced by another user or provider identity.
  - Define deterministic cancellation, repeated-delete, not-found, success, and failure behavior, preserving durable data on rollback.
  - Add a PostgreSQL-backed request/browser scenario covering manual creation in every state, exactly-one-shelf visibility, state changes, repeated requests, deletion, failure rollback, and cross-user access with no provider or OIDC dependency.

Exit criteria:

- The manual path works end to end without provider or authentication infrastructure.
- A created book appears in exactly one shelf, moves without creating another UserEdition, and obeys all date rules.
- A failed or repeated request cannot leave partial data or create a duplicate user link.
- Book deletion removes only the current user's private record and data.

### Milestone 3 — Complete shelf experience

**Outcome:** The three primary sections match the PRD and the main mobile/desktop mockup behavior.

Tasks:

- **3-1 — Complete shelf presentation.**
  - Build one reusable cover-card component for Reading, To Read, and Finished using a stored cover or local placeholder, effective metadata, state text, and detail link.
  - Show the PRD-defined context-specific dates and a server-derived approximate, non-editable added age in To Read.
  - Add concise shelf-specific empty states with Add Book links, plus an Add Book action for an empty selected Finished year.
  - Verify every shelf, long/missing/escaped metadata, missing dates, today/month/year/old-record age boundaries, default ordering, and tied sort fields.
- **3-2 — Add the Reading quick-finish flow.**
  - Add a shelf-level action that captures the required finish date and delegates to the existing transition service without duplicating transition rules.
  - Keep the user on a consistent shelf/detail state after success or failure.
  - Verify success, invalid dates, cancellation, stale/repeated submission, rollback, shelf consistency, and cross-user denial.
- **3-3 — Implement the Finished year view.**
  - Derive the default year from the configured application timezone after resolving the current user, keeping timezone resolution outside persistence and ready for a future per-user preference.
  - Parse and validate optional year query parameters, and provide deterministic no-JavaScript selection through ordinary links.
  - Derive available years from the current user's Finished records, always include the configured-zone current year, and reject unavailable selections.
  - Produce the selected-year count from the same filtered result set used to render visible books.
  - Test the configured-zone New Year boundary, sparse/no records, invalid selections, empty years, count-equals-visible-items, and cross-user isolation.
- **3-4 — Verify the complete shelf experience.**
  - Scale navigation, shelf controls, cover cards, and empty states from the supported narrow-mobile width through desktop without clipping or horizontal page scrolling.
  - Add browser assertions or screenshots at the documented breakpoint set.
  - With JavaScript disabled, verify shelf navigation, year selection, detail and Add Book links, state changes, and confirmations through server-rendered fallbacks.
  - Add a focused PostgreSQL-backed regression suite for ordering, local-date/year boundaries, Finished filtering, visible counts, and two-user isolation.

Exit criteria:

- Reading, To Read, and Finished contain exactly the correct current records in the required order.
- Only Finished has a year selector and annual count.
- The count always equals the books visible for the selected year.
- The primary workflow is usable at a narrow mobile width and at desktop width.

### Milestone 4 — Book detail, private notes, and metadata overrides

**Outcome:** Users can inspect and maintain all private information attached to one library book.

Tasks:

- **4-1 — Build the book detail page.**
  - Render one owned UserEdition's effective metadata, current state, applicable dates, private notes, provenance-neutral cover/placeholder, and state-appropriate actions.
  - Never reveal canonical/provider provenance through cover presentation where the PRD does not call for it.
  - Verify all three states, stored cover and placeholder, missing/long optionals, escaped private content, not-found behavior, and cross-user lookup denial.
- **4-2 — Implement private notes and metadata editing.**
  - Add plain-text private-note editing with documented length/newline rules, clear success/error feedback, and submitted-value preservation on validation or persistence failure.
  - Add private UserEdition overrides for every supported scalar field without mutating canonical Edition data.
  - Support ordered multiple-author overrides and inherited, explicit-value, and permitted explicit-blank semantics.
  - Add per-field and full-author-list reset actions that return to the not-overridden state and immediately follow subsequent canonical changes.
  - Prevent any combined edit from leaving the effective title or author list empty.
  - Test note round trips/escaping, every field and override state, author add/remove/reorder, canonical immutability, reset behavior, combined validation, and transactional rollback.
- **4-3 — Implement detail-page dates and state actions.**
  - Allow only date edits permitted by the current state, including adding a previously unknown start date to a Finished book.
  - Present exact date consequences and required new dates for every transition, requiring confirmation before dates are discarded.
  - Update the existing UserEdition through shared domain/application rules rather than duplicating them in the web layer.
  - Verify every valid/invalid date combination and transition plus cancellation, repeat/stale requests, rollback, and detail/shelf/year-count consistency.
- **4-4 — Complete detail deletion and privacy verification.**
  - Integrate the existing confirmation and deletion operations into the detail flow with ordinary page/form behavior and clear cancellation.
  - Define deterministic repeat-delete and not-found outcomes.
  - Using two users linked to the same Edition, verify notes, scalar/author overrides, dates, transitions, resets, and deletion affect only the acting user's detail and shelf results.
  - Prove canonical metadata and the other user's shared Edition reference remain unchanged.

Exit criteria:

- Every supported field can be viewed and privately overridden under its validation rules.
- Resetting an override restores the canonical value.
- Notes and overrides never leak between the two seeded users.
- Detail actions update the existing UserEdition and keep the shelf/count views consistent.

### Milestone 5 — Private-library search and layout preference

**Outcome:** Users can search only their own effective metadata and choose a persistent shelf layout.

Tasks:

- **5-1 — Implement private-library search.**
  - Add a user-scoped, case-insensitive partial search across effective title and ordered author values with documented whitespace and empty-query behavior.
  - Return records across all three states, label each result's state in text, and define deterministic ordering plus pagination or a documented result limit.
  - Keep private search separate from Add Book/provider search in routes, form actions, application interfaces, and response models.
  - Preserve the escaped query in a no-results state with an explicit Add Book action and no implicit provider request; pre-fill manual entry where supported.
  - Verify canonical and overridden titles/authors, explicit blanks, partial terms, mixed states, ordering/limits, escaped input, the provider-call boundary, and cross-user isolation.
- **5-2 — Validate search performance.**
  - Build a representative canonical-plus-private-override fixture at the expected data size.
  - Capture `EXPLAIN (ANALYZE, BUFFERS)` plans, adjust PostgreSQL indexes or query representation as needed, and retain before/after evidence plus the final fixture size.
  - Add matching and ordering regression tests so later changes cannot silently bypass the intended plan.
- **5-3 — Implement and verify the shelf layout preference.**
  - Build the compact-list component from the mockup for all three shelves using the same records/actions as cover cards.
  - Persist one user-scoped cover-card/compact-list preference, define the new-user default, and apply it across all shelves and later sessions.
  - Verify every state, long metadata, narrow/desktop rendering, invalid and repeated preference changes, search-to-shelf behavior, session persistence, and two-user isolation.

Exit criteria:

- Search finds canonical and privately overridden titles/authors for only the current user.
- A no-result private query is never sent to an external provider.
- The selected layout remains consistent across shelves and later sessions for that user.

### Milestone 6 — Responsive PWA and resilience

**Outcome:** The provider-independent application is installable, robust, and usable across the supported mobile and desktop browsers without promising offline data behavior.

Tasks:

- **6-1 — Complete responsive provider-independent flows.**
  - Complete every milestone 2–5 flow at documented narrow-mobile, intermediate, and desktop widths, including long metadata, validation messages, confirmations, and destructive-operation responses.
  - Standardize provider-independent empty, enhancement-loading, validation, connectivity, not-found, unauthorized, conflict, and unexpected-error states.
  - Escape all user text and render descriptions as plain/untrusted content.
  - Add representative browser assertions or screenshots for every flow and viewport, with no clipping or horizontal page scrolling.
- **6-2 — Package Rosie's books as an online PWA.**
  - Add the web app manifest, all required icon sizes, theme/background metadata, standalone display mode, start URL, and mobile safe-area behavior under the Rosie's books name.
  - Decide and record whether a service worker is needed.
  - If used, cache only versioned public static assets, bypass private/authenticated pages and mutations, and define/test cache update behavior; if omitted, record why installability and resilience still hold.
  - Present an accurate online-required state after load-time or submission-time connectivity loss, preserve safe form input where practical, never queue mutations, and make no offline-data claim.
  - Verify manifest/icon responses, installability, cache boundaries/updates, disconnection, recovery, and ordinary browser-tab operation.
- **6-3 — Verify PWA resilience and fallback behavior.**
  - Run every essential provider-independent navigation, search, form, state change, preference, and destructive confirmation with progressive enhancement disabled and implement any required server-rendered fallback.
  - Add a repeatable critical add/view/search/edit/transition/delete browser journey at representative narrow-mobile and desktop widths in both normal-JavaScript and no-JavaScript modes.
  - Confirm private data is not presented as available offline and no mutation is cached or replayed as an offline data store.

Exit criteria:

- Rosie's books is installable and fully usable in a normal browser tab.
- It makes no claim that private data or mutations work offline.
- Essential provider-independent workflows work with JavaScript enhancements unavailable.
- All milestone 2–5 flows pass the narrow-mobile and desktop smoke suite without clipping or horizontal page scrolling.

### Milestone 7 — Provider research, provider add flow, and durable covers

**Outcome:** Users can find an edition through a selected provider and retain all accepted metadata and available cover data locally.

#### 7A. Provider research spike

- **7-1 — Select the provider and define its boundary.**
  - Define an evaluation matrix covering edition-level identity, title/author metadata, identifier quality, publication-date precision, format/publisher/page/language/description coverage, cover availability and permitted use, pagination, rate limits, authentication, reliability, and operating constraints.
  - Evaluate viable providers with representative title, author, ambiguous-edition, missing-ISBN, and missing-cover queries; record dated evidence and limitations without credentials.
  - Commit a decision record naming the selected provider, rejected alternatives, rationale, cover-use constraints, configuration/credentials, and risks requiring operational monitoring.
  - Define application-owned search, result, selected-edition, pagination, and error types plus normalization rules, with architecture/compile checks preventing provider types from leaking into domain or web code.
  - Define deterministic identity/deduplication behavior for normalized ISBN-13, provider edition ID, missing identifiers, and conflicts, explicitly prohibiting fuzzy merging of manual editions.
  - Define bounded connection/request timeouts, retryable/non-retryable failures, retry count/backoff, result limits/pagination, rate-limit handling, and configuration validation with safe defaults.
  - Commit provider fixtures, adapter contract tests, identity decision-table tests, and operating-policy/configuration tests.

The spike is complete only when task 7-1 is committed and the adapter contract can represent the selected provider's useful edition data without leaking provider-specific types into the domain or web layers.

#### 7B. Provider integration and cover persistence

- **7-2 — Implement provider search and edition selection.**
  - Implement partial title, author, and combined searches plus result limits/pagination behind the provider-neutral adapter, normalizing missing/malformed fields and provider errors.
  - Build separate Add Book loading, results, no-match, timeout, rate-limit, and unavailable states without coupling them to private-library search.
  - Preserve the query across retry and manual fallback, pre-fill the manual title where practical, and never automatically retry a non-retryable error or duplicate a provider call.
  - Show normalized title, ordered authors, identifiers, publication precision, format, and other available disambiguating fields without depending on a cover.
  - Add a tamper-resistant review step for one result that displays accepted metadata and captures every valid initial state/date combination.
  - Verify the adapter contract, representative/malformed fixtures, retries and provider failures, query preservation, similar/missing/long metadata, tampering, and responsive/no-JavaScript flows.
- **7-3 — Persist provider books safely.**
  - Resolve canonical identity first by normalized ISBN-13 and then by provider identity as applicable, never fuzzy-merging a manual Edition.
  - Create or reuse the canonical Edition, store accepted normalized metadata locally, and link the current user in one transaction.
  - Detect an existing current-user link and open it without mutation or duplication.
  - When reusing a canonical Edition, apply a documented field-by-field fill-missing matrix; never overwrite populated canonical metadata or private overrides unexpectedly.
  - Make existing shelf, detail, and private-search pages independent of provider availability.
  - Verify new/reused editions, repeat and concurrent selections, retries, stale results, missing/conflicting identities, every merge decision, rollback, two-user behavior, and normal library use after complete provider shutdown.
- **7-4 — Fetch, store, and serve durable covers.**
  - Accept cover URLs only from trusted adapter output and expose no general URL-fetch operation.
  - Restrict schemes, approved destinations, DNS/IP resolution, and redirect destinations; reject local/private addresses, alternate schemes, and unapproved hosts.
  - Bound connection/read time, bytes, decoded dimensions, and decompression risk; accept only supported raster content.
  - Store bytes, validated MIME type, dimensions, content hash, provenance URL, and fetch timestamp in PostgreSQL, handling duplicate content deterministically.
  - Make all cover failures non-fatal, record only a non-sensitive outcome, render the local placeholder, and support a later successful refetch if that behavior is retained.
  - Serve stored covers through an ownership-aware or appropriately content-addressed route with correct MIME type, cache headers, responsive image markup, and lazy loading; never hotlink a normal library page.
  - Test valid, slow, oversized, decompression-risk, invalid-content, local/private/redirect/alternate-scheme/unapproved-host, duplicate-content, authorization, cache, placeholder, and offline-from-provider cases.

Exit criteria:

- Provider results distinguish editions and accepted books survive complete provider unavailability.
- Retrying or re-selecting an existing edition cannot duplicate the user's link.
- No normal library page hotlinks a provider cover.
- Missing, slow, oversized, or invalid covers cannot prevent the book from being saved.

### Milestone 8 — OIDC authentication and account lifecycle

**Outcome:** The development identity boundary is backed by a selected OIDC provider, allowlisted users receive isolated sessions, and users can log out or delete their accounts.

Tasks:

- **8-1 — Select and integrate the OIDC identity boundary.**
  - Reconfirm authentication requirements and evaluate providers against browser-tab and installed-PWA constraints.
  - Commit a decision record covering the selected provider, required claims, logout/revocation capabilities, operational configuration, and known limitations.
  - Compare the decision with `PRD.md`; update its Google-specific or other authentication requirements and acceptance criteria before implementation when needed, or record that no change is required.
  - Integrate Quarkus-compatible OIDC validation and map normalized issuer plus subject to the durable local User identity rather than using email as the durable key.
  - Require a verified, normalized email present in deployment configuration and create no local account for missing, unverified, malformed, or non-allowlisted identities.
  - Keep seeded users and the development identity selector restricted to development/test profiles and make production startup fail if enabling configuration is present.
  - Verify first and repeat login, changed email, issuer collision, invalid tokens, every allowlist/verification denial, and production profile safeguards.
- **8-2 — Implement secure sessions and authentication flows.**
  - Implement login, callback, access-denied, persisted server-side session lookup/revocation, and logout behavior.
  - Validate state and nonce and handle provider errors, expired/replayed callbacks, revoked sessions, and session expiry deterministically.
  - Use secure HTTP-only cookies, the documented SameSite policy, rotation/fixation protection, and CSRF protection on every mutation.
  - Preserve only valid internal GET destinations through login in browser-tab and installed-PWA modes; safely reject external, malformed, mutation, or stale targets.
  - Emit structured login, denial-category, callback-failure, logout, revocation, and expiry outcomes without tokens, cookies, notes, or unnecessary search-query content.
  - Test the full lifecycle with the provider harness, cookie attributes, redirect validation, cross-site mutations, and captured-log redaction.
- **8-3 — Implement account settings and deletion.**
  - Add the minimal settings page with the identity/session information required by the PRD and a deliberate permanent-deletion confirmation flow.
  - Revoke/delete the user's sessions, UserEditions, overrides, notes, dates, and preferences transactionally.
  - Remove canonical Editions and stored covers only when orphaned, preserving another user's shared references.
  - Record only deletion outcome, timestamp, and non-content operational identifiers; retain no deleted notes, queries, overrides, or metadata content.
  - Verify owned-route access, cancellation, invalid confirmation, expired session, rollback, repeated deletion, concurrent sessions, shared editions/covers, and audit/log payload privacy.
- **8-4 — Verify OIDC-backed ownership end to end.**
  - Re-run resource enumeration and every two-user read/write isolation scenario using two real OIDC-backed test identities or the equivalent provider harness.
  - Verify login, callback, session persistence, safe redirects, revocation, logout, allowlist denial, and account deletion at supported narrow-mobile and desktop widths.
  - Repeat installed/standalone-mode checks wherever the provider decision says that mode is supported.
  - Prove the production identity boundary preserves all ownership guarantees established with development identities.

Exit criteria:

- An allowlisted user can sign in, retain a revocable session, access only their library, and log out.
- A non-allowlisted or unverified identity receives no account or library access.
- Development identity mechanisms cannot operate in the production profile.
- Account deletion removes the user's private application data and active sessions without harming another user's shared Edition reference.

### Milestone 9 — Release packaging, operations documentation, and acceptance

**Outcome:** Rosie's books is deliverable as a production-oriented Docker Compose service with documented operation and evidence that the in-scope PRD release criteria pass.

Tasks:

- **9-1 — Build the production image and Compose topology.**
  - Produce a reproducible minimal JVM fast-jar application image with a pinned Java runtime base, immutable application files, and a non-root runtime user where practical; no native-image build is required.
  - Add application and PostgreSQL Compose services with persistent storage, health checks, restart policy, dependency readiness, explicit networks/ports, and configuration inputs.
  - Provide a safe example environment containing every required variable with non-secret placeholders and descriptions, plus startup validation for missing or unsafe values.
  - Add repeatable image build/start/readiness smoke checks and a repository/example secret-pattern check with documented intentional test-fixture matches.
- **9-2 — Write and validate the operations runbook.**
  - Document when/how migrations run, single-runner expectations, failure behavior, forward-fix policy, and database/application rollback compatibility.
  - Document operation behind an HTTPS reverse proxy, including trusted forwarded headers, external scheme/URL handling, secure-cookie assumptions, OIDC callback construction, request/body limits, and local-cover caching.
  - Document every allowlist, OIDC, book-provider, cover-limit, database, logging, health, session, and PWA configuration value with required/optional status, default, secret handling, and restart impact.
  - Document exact PostgreSQL backup/restore procedures.
  - Validate a clean install, one representative upgrade, trusted and spoofed forwarding, and restoration of representative users, editions, overrides, notes, and covers with source/restored data checks.
- **9-3 — Verify production security, observability, and performance.**
  - Scan tracked files, built artifacts, example configuration, and captured logs for credentials, tokens, cookies, and deployment-specific allowlists.
  - Exercise authentication outcomes, provider latency/failures, validation categories, readiness changes, and unexpected errors.
  - Confirm structured fields and correlation are operationally useful while notes, tokens, cookies, credentials, and unnecessary search content are absent.
  - Define the representative two-user data fixture and request mix, measure database-backed shelf responses against the 500 ms p95 target in the packaged topology, and record method, environment, results, and any required query/configuration fix.
- **9-4 — Execute the release checklist.**
  - From a clean database, run migrations and the complete unit, integration, request, security, and browser suites against the release candidate.
  - Repeat the designated smoke tests against the packaged Compose service and record the exact commands and results.
  - Map every in-scope `PRD.md` acceptance criterion to automated evidence or a precise manual check, explicitly marking accessibility clauses deferred, and execute the checklist.
  - Compare routes, schema, configuration, and visible features with the PRD goals/non-goals and remove accidental scope additions or document an approved requirements change.
  - Link every failure to a fix or explicit PRD exception and commit final pass/fail and release-boundary evidence.

Exit criteria:

- `docker compose up` can start the documented production-oriented topology after required configuration is supplied.
- Persistent data survives application/container replacement and the documented backup can be restored.
- Health checks distinguish startup/readiness from application failure.
- All in-scope PRD acceptance criteria pass, or any exception is explicitly accepted and reflected in the PRD before release; accessibility-specific clauses remain explicitly deferred.
- No live-host provisioning or deployment is required for this milestone to be complete.

## 4. Definition of done for every milestone

A milestone is complete only when:

- Its tasks and exit criteria are satisfied, not merely coded.
- Its implementation follows `ENGINEERING_CONVENTIONS.md`, or an exception is explicitly recorded.
- New behavior has proportionate unit, persistence, request, and/or browser tests.
- User-owned queries and mutations include a negative cross-user test when applicable.
- Schema changes are expressed as forward versioned migrations and work from a clean database.
- Server-rendered behavior remains correct without optional JavaScript enhancements.
- Error paths preserve user input or existing durable data where the PRD requires it.
- Relevant local-development or operating documentation is updated.
- The milestone introduces no secrets, private-data logging, provider hotlinks, or features outside the release boundary.

## 5. Deferred decisions and decision points

These are deliberately unresolved until the milestone that needs them:

| Decision | Decide in | Required output |
| --- | --- | --- |
| Exact framework/library versions, build tool, migration tool, jOOQ generation/integration setup, and integration-test setup | Milestone 0 | Recorded technical choices and reproducible commands |
| External book provider | Milestone 7, task 7-1 | Decision record, adapter contract, normalization and operating rules |
| OIDC provider | Milestone 8 | Updated requirements if needed, decision record, claim/session/configuration rules |

The host, DNS, TLS termination product, and live deployment procedure are outside this plan. The release documentation will state the reverse-proxy and HTTPS contract needed by the Compose service.

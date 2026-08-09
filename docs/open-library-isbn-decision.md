# Open Library exact-ISBN decision — 2026-08-09

Rosie's Books uses Open Library for low-volume, server-side lookup of a single concrete edition by a valid, normalized ISBN-13. It needs no API key. Requests identify the operator with `User-Agent: RosiesBooks (<contact>)`; production requires a nonblank contact configuration.

The adapter searches with the ISBN constraint, retrieves the candidate edition record, and accepts it only if that edition's ISBN-10 or ISBN-13 normalizes to the requested ISBN-13. Open Library JSON, URLs, DTOs, and HTTP details are confined to `provider.openlibrary`. The public boundary exposes provider-neutral edition data, outcome categories, and a constrained HTTPS cover reference only.

Policy: connect timeout is two seconds, each request has a five-second timeout, and this process issues at most three requests per second. Transport and 5xx failures get one 250–500 ms jittered retry. A 429 is returned with `Retry-After` when supplied; malformed responses, 4xx failures, and not-found results are not retried. Caught lookup exceptions are logged server-side. Development logs include full exception details by default; production logs only the exception class. Set `PROVIDER_OPEN_LIBRARY_LOG_FULL_EXCEPTION_DETAILS` to override either profile.

Accepted covers will be fetched once and stored locally in task 7-4; ordinary library pages never hotlink them. Cover-rights details remain an operational uncertainty to monitor. One accepted-cover fetch at this two-user volume is not crawling or bulk access.

Identity is deterministic: normalized ISBN-13 first; otherwise non-null `(provider_name, provider_edition_id)`; identifierless/manual editions are never fuzzy merged. If the two identifiers resolve to different editions, the add flow must surface an identifier conflict.

Deferred: Add Book will later offer **Search by ISBN** and **Search by title/author**. Title/author search must find works and show a bounded, paginated concrete-edition picker; it must not silently select a representative edition. Camera scanning is a separate progressive-enhancement task: EAN-13 detection may populate the ISBN field, typed entry remains available, and camera frames never leave the device.

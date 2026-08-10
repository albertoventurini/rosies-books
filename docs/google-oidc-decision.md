# Google OIDC decision

Google OpenID Connect is Rosie's books' only identity provider. The application is a confidential,
server-rendered web application: it keeps the client secret server-side and uses authorization-code
flow with PKCE. Quarkus is configured with the Google issuer `https://accounts.google.com` and
discovers metadata at `https://accounts.google.com/.well-known/openid-configuration`.

The requested scopes are exactly `openid`, `profile`, and `email`. Quarkus validates the OIDC token
before application code sees it. At the identity boundary we require the exact issuer
`https://accounts.google.com`, a nonblank `sub`, a normalized valid `email`, and
`email_verified=true`. A local user is keyed by `(oidc_issuer, oidc_subject)`, never by email.
The email is an allowlist gate and a mutable display/contact value.

Create separate Google OAuth **Web application** clients for production and local verification.
Register the exact production HTTPS callback URI ending in `/oidc/callback`; for the local client,
register `http://localhost:<port>/oidc/callback`. The local profile is intentionally opt-in and
does not enable development seeded users or `/dev/users`.

Operators supply the client ID, client secret, OIDC state-encryption secret, and comma-separated
allowlist through a secret manager or an untracked environment file; see `.env.oidc.example` for
names only. Production fails at startup if these required values or a valid nonempty allowlist are
missing. It also has no development selector or seeded identities.

Google can end the browser's Google session through its own account/logout experience and can revoke
OAuth grants through its revocation endpoint, but it does not provide an application-specific
server-session logout guarantee. Rosie's books will add its own persistent, revocable sessions and
logout workflow in task 8-2. This decision makes no PWA compatibility claim.

## Manual local acceptance check

1. Create the separate local Web client and register the localhost callback above.
2. Export the `ROSIES_BOOKS_GOOGLE_OIDC_LOCAL_*` values from the example file, including the
   tester's allowed Google email, then run `./mvnw quarkus:dev -Dquarkus.profile=local-oidc`.
3. Open the application, sign in with the allowed account, and confirm both first and repeat access.
4. Sign in with a non-allowlisted account and confirm access is denied without creating a user.

This is the interoperability check with Google; automated tests use Quarkus' local OIDC test server
and remain offline and deterministic.

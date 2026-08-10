#!/usr/bin/env bash
set -euo pipefail

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=application_password="$ROSIES_BOOKS_DATABASE_PASSWORD" \
  --set=database_name="$POSTGRES_DB" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE ROLE rosies_books_app LOGIN PASSWORD :'application_password';
GRANT CONNECT ON DATABASE :"database_name" TO rosies_books_app;
GRANT USAGE, CREATE ON SCHEMA public TO rosies_books_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rosies_books_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO rosies_books_app;
SQL

#!/usr/bin/env bash
set -euo pipefail

image_name="${ROSIES_BOOKS_IMAGE:-rosies-books:latest}"
docker build --tag "$image_name" .

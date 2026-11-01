#!/usr/bin/env bash
exec "$(dirname "${BASH_SOURCE[0]}")/common/health-check.sh" "$@"

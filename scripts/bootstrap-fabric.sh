#!/usr/bin/env bash
exec "$(dirname "${BASH_SOURCE[0]}")/ops/bootstrap-fabric.sh" "$@"

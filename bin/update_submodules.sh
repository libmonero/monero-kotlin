#!/usr/bin/env bash
# Initializes the submodules of the project, which are big: external/monero-c and the ones that it has.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
git submodule update --init --recursive

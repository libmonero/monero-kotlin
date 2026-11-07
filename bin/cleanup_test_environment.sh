#!/usr/bin/env bash
# Removes the containers and the volumes that bin/setup_test_environment.sh started, which also removes the chains of the nodes and the wallets
# of the wallet RPC servers.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
export COMPOSE_FILE=${COMPOSE_FILE:-src/test/docker/docker-compose.yml}

docker compose down -v

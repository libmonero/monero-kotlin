#!/usr/bin/env bash
# Starts the regtest nodes and the wallet RPC servers of src/test/docker/docker-compose.yml, which the integration tests use, and waits until
# they answer. It needs Docker and the ports 18080 to 18086, 18089 and 48080. Stop them with bin/cleanup_test_environment.sh.
#
#   MONERO_TAG    the tag of the images, master by default
#   COMPOSE_FILE  the compose file, which is the one of src/test/docker by default
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
export COMPOSE_FILE=${COMPOSE_FILE:-src/test/docker/docker-compose.yml}

docker compose up -d node_1 node_2 xmr_wallet_1 xmr_wallet_2 xmr_wallet_3

# the nodes and the servers have a login: curl does the digest authentication that monerod asks for
wait_for() {
    for _ in $(seq 1 60); do
        if curl -fsS -m 5 --digest -u "$2" -X POST "http://127.0.0.1:$1/json_rpc" -d "{\"jsonrpc\":\"2.0\",\"id\":\"0\",\"method\":\"$3\"}" 2> /dev/null | grep -q "$4"; then
            return 0
        fi
        sleep 2
    done
    echo "nothing answered on the port $1" >&2
    return 1
}

wait_for 18081 rpc_daemon_user:abc123 get_block_count '"count"'
for port in 18082 18083 18084; do
    wait_for "$port" rpc_user:abc123 get_version '"version"'
done
echo "The regtest nodes and the wallet RPC servers are up"

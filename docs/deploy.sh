#!/usr/bin/env bash

# Fail if any command inside fails
set -e

function copyToServer() {
    echo "Copying $1 to $CD_URL at location $2"
    scp -P "$CD_PORT" "$1" "$CD_USER@$CD_URL:$2"
}

function executeOnServer() {
    echo "Executing on server: '$1'"
    ssh -p "$CD_PORT" "$CD_USER@$CD_URL" "$1"
}

DOCKER_SERVER_DIR="/home/pse_test/velcom/.docker"

executeOnServer "mkdir -p $DOCKER_SERVER_DIR"

copyToServer "backend/backend/target/backend.jar" "$DOCKER_SERVER_DIR"
copyToServer "backend/runner/target/runner.jar" "$DOCKER_SERVER_DIR"
tar -cf dist.tar "frontend/dist"
copyToServer "dist.tar" "$DOCKER_SERVER_DIR"

executeOnServer "tar -xf $DOCKER_SERVER_DIR/dist.tar"
executeOnServer "mv $DOCKER_SERVER_DIR/frontend/dist $DOCKER_SERVER_DIR/dist"

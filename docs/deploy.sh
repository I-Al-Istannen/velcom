#!/usr/bin/env bash

# This is our Continous Deployment deploy script.
# It just calls out to the server and requests the docker image to be pulled
# again and restarts the service.

# Fail if any command inside fails
set -e

function executeOnServer() {
    echo "Executing on server: '$1'"
    ssh -p "$CD_PORT" "$CD_USER@$CD_URL" "$1"
}

if [ -z "$1" -o -z "$2" ]; then
    echo "Usage: $0 <github actor name> <github packes access token>"
    exit 1
fi

GITHUB_NAME="$1"
GITHUB_TOKEN="$2"

# Login to github registry
executeOnServer "echo "$GITHUB_TOKEN" | docker login docker.pkg.github.com -u "$GITHUB_NAME" --password-stdin"

# Build it
executeOnServer "sudo /home/pse_test/velcom/update-docker-image.sh"

# Restart the docker container :)
executeOnServer "sudo systemctl restart pse-test-velcom.service"

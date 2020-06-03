#!/usr/bin/env bash

# fail on first error
set -e

# Pull image
sudo docker pull docker.pkg.github.com/i-al-istannen/velcom/velcom-server:latest

# Clean up old images
sudo docker image prune --filter "until=10m" --filter "label=velcom-server" -f

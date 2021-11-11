#!/bin/sh
echo "Adding tag for $1"
git tag -a "$1" -m "Release of version: $1"
git push origin "$1"
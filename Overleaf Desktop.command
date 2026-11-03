#!/bin/sh
# macOS Finder entry point; dependencies are documented in docs/MACOS.md.
cd "$(dirname "$0")" || exit 1
if sh scripts/run-client.sh; then
    exit 0
else
    printf '\n%s\n' 'Could not start Overleaf Desktop. See the error above and docs/MACOS.md.'
    printf '%s' 'Press Return to close... '
    read -r reply
    exit 1
fi

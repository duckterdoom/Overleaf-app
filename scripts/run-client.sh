#!/usr/bin/env sh
# Build all modules (no tests), then launch the JavaFX client.
set -eu
cd "$(dirname "$0")/.."
mvn -q -DskipTests install
mvn -q -pl modules/client javafx:run

#!/usr/bin/env sh
# Build the standalone server and console demo JARs into dist/ (no Maven, JavaFX or MongoDB needed).
set -eu
cd "$(dirname "$0")/.."
mkdir -p target/network-classes dist
find modules/common/src/main/java modules/server/src/main/java modules/client/src/main/java/com/overleaf/client/net -name '*.java' > target/network-sources.txt
javac --release 17 -encoding UTF-8 -d target/network-classes @target/network-sources.txt
jar --create --file dist/overleaf-server.jar --main-class com.overleaf.server.ServerApp -C target/network-classes com/overleaf/common -C target/network-classes com/overleaf/server
jar --create --file dist/overleaf-network-demo.jar --main-class com.overleaf.client.net.NetworkDemo -C target/network-classes com/overleaf/common -C target/network-classes com/overleaf/client/net
echo 'Built dist/overleaf-server.jar and dist/overleaf-network-demo.jar (Java 17+)'

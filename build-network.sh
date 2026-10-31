#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p target/network-classes dist
find overleaf-common/src/main/java overleaf-server/src/main/java overleaf-client/src/main/java/com/overleaf/client/net -name '*.java' > target/network-sources.txt
javac --release 17 -encoding UTF-8 -d target/network-classes @target/network-sources.txt
jar --create --file dist/overleaf-server.jar --main-class com.overleaf.server.ServerApp -C target/network-classes com/overleaf/common -C target/network-classes com/overleaf/server
jar --create --file dist/overleaf-network-demo.jar --main-class com.overleaf.client.net.NetworkDemo -C target/network-classes com/overleaf/common -C target/network-classes com/overleaf/client/net
echo 'Built dist/overleaf-server.jar and dist/overleaf-network-demo.jar (Java 17+)'

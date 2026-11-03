#!/usr/bin/env sh
# Build on this machine so Maven selects native JavaFX libraries for its OS/architecture.
set -eu
cd "$(dirname "$0")/.."

mode=run
case "${1:-}" in
    --check) mode=check ;;
    --build-only|-BuildOnly) mode=build ;;
    --help|-h)
        printf '%s\n' 'Usage: sh scripts/run-client.sh [--check|--build-only]' \
            '--check: report JDK/Maven and optional TeX tools without building.'
        exit 0 ;;
    '') ;;
    *) printf '%s\n' "Unknown option: $1" >&2; exit 2 ;;
esac

if [ "$(uname -s)" = Darwin ]; then
    # Finder-launched .command files may have a shorter PATH than an interactive Terminal.
    for directory in /opt/homebrew/bin /usr/local/bin; do
        if [ -d "$directory" ]; then PATH="$PATH:$directory"; fi
    done
    if [ ! -x "${JAVA_HOME:-}/bin/javac" ]; then
        if command -v brew >/dev/null 2>&1; then
            jdk_prefix=$(brew --prefix openjdk@21 2>/dev/null || true)
            if [ -n "$jdk_prefix" ] && [ -x "$jdk_prefix/libexec/openjdk.jdk/Contents/Home/bin/javac" ]; then
                JAVA_HOME="$jdk_prefix/libexec/openjdk.jdk/Contents/Home"
            fi
        fi
        if [ ! -x "${JAVA_HOME:-}/bin/javac" ] && [ -x /usr/libexec/java_home ]; then
            JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
        fi
    fi
    if [ -d /Library/TeX/texbin ]; then PATH="$PATH:/Library/TeX/texbin"; fi
fi
if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/javac" ]; then
        printf '%s\n' 'JAVA_HOME must point to a full JDK 21+, not a JRE.' >&2
        exit 1
    fi
    PATH="$JAVA_HOME/bin:$PATH"
    export JAVA_HOME
fi
export PATH
if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    printf '%s\n' 'Install JDK 21+ and set JAVA_HOME. See docs/MACOS.md.' >&2
    exit 1
fi
jdk_version=$(javac -version 2>&1 | awk '/^javac / {print $2; exit}')
jdk_major=$(printf '%s' "$jdk_version" | sed 's/[^0-9].*//')
if ! [ "${jdk_major:-0}" -ge 21 ] 2>/dev/null; then
    printf '%s\n' "JDK 21+ is required; found javac $jdk_version. See docs/MACOS.md." >&2
    exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
    printf '%s\n' 'Maven was not found. On macOS: brew install maven. See docs/MACOS.md.' >&2
    exit 1
fi
printf '%s\n' "Platform: $(uname -s) / $(uname -m)" "JDK: $jdk_version" "Java: $(command -v java)"
if [ "$mode" = check ]; then
    mvn --version
    for tool in latexmk pdflatex xelatex lualatex biber synctex; do
        if command -v "$tool" >/dev/null 2>&1; then
            printf '%s\n' "$tool: $(command -v "$tool")"
        else
            printf '%s\n' "$tool: not on PATH (optional for editing; required for relevant compile/SyncTeX features)"
        fi
    done
    exit 0
fi
mvn -B "-Dmaven.repo.local=$PWD/target/maven-cache" -DskipTests install
if [ "$mode" = build ]; then exit 0; fi
exec mvn -B "-Dmaven.repo.local=$PWD/target/maven-cache" -pl modules/client javafx:run

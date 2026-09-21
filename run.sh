#!/usr/bin/env bash
#
# PortfolioBoss — build with Maven and run the read-only portfolio reader.
#
# Usage:
#   ./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
#   ./run.sh 7497         # paper-trading port
#   ./run.sh 7496 102     # custom clientId
#
# Requires: Maven (`brew install maven`), a running IB TWS / Gateway with "Enable ActiveX and
# Socket Clients" on, and the TWS API jar at ~/DevTools/twsapi/TwsApi.jar (same one IBBot uses).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEVTOOLS="${HOME}/DevTools"
TWSAPI_JAR="${DEVTOOLS}/twsapi/TwsApi.jar"
TWSAPI_MAVEN_DIR="${HOME}/.m2/repository/com/interactivebrokers/tws-api/local"

if [[ ! -f "${TWSAPI_JAR}" ]]; then
  echo "ERROR: TWS API jar not found at ${TWSAPI_JAR}" >&2
  exit 1
fi

# The TWS API is not on Maven Central, so the jar is registered once in the local Maven repository
# (~/.m2, on this machine only). After replacing the jar with a newer TWS API, delete
# ${TWSAPI_MAVEN_DIR} and it is registered again on the next run.
if [[ ! -d "${TWSAPI_MAVEN_DIR}" ]]; then
  echo "› registering the TWS API jar in the local Maven repository (one time)…"
  mvn -q install:install-file -Dfile="${TWSAPI_JAR}" \
      -DgroupId=com.interactivebrokers -DartifactId=tws-api -Dversion=local -Dpackaging=jar
fi

echo "› building and running…"
cd "${HERE}"   # Main looks for ui/ relative to the working directory
mvn -q spring-boot:run -Dspring-boot.run.arguments="$*"

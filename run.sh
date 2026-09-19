#!/usr/bin/env bash
#
# PortfolioBoss — compile + run the Milestone 0 read-only portfolio reader.
#
# Usage:
#   ./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
#   ./run.sh 7497         # paper-trading port
#   ./run.sh 7496 102     # custom clientId
#
# Requires: a running IB TWS / Gateway with "Enable ActiveX and Socket Clients" on,
# and the TWS API jar at ~/DevTools/twsapi/TwsApi.jar (same one IBBot uses).

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEVTOOLS="${HOME}/DevTools"
TWSAPI_JAR="${DEVTOOLS}/twsapi/TwsApi.jar"
OUT="${HERE}/out"

if [[ ! -f "${TWSAPI_JAR}" ]]; then
  echo "ERROR: TWS API jar not found at ${TWSAPI_JAR}" >&2
  exit 1
fi

# Classpath: the IB API jar, plus the protobuf runtime dir if one is present.
CP="${TWSAPI_JAR}"
if compgen -G "${DEVTOOLS}/google-proto-buf/*.jar" > /dev/null 2>&1; then
  CP="${CP}:${DEVTOOLS}/google-proto-buf/*"
fi

mkdir -p "${OUT}"
echo "› compiling…"
find "${HERE}/src/main/java" -name '*.java' > "${OUT}/sources.txt"
javac -d "${OUT}" -cp "${CP}" @"${OUT}/sources.txt"

echo "› running…"
cd "${HERE}"   # Main looks for ui/ relative to the working directory
java -cp "${OUT}:${CP}" portfolioboss.Main "$@"

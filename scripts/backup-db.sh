#!/usr/bin/env bash
#
# PortfolioBoss — back up the database to ~/PortfolioBossBackups.
#
# The sector and trades typed in by hand cannot be rebuilt from IB, so this is the data to keep safe.
# The backup goes outside the repo on purpose: it holds portfolio data.
#
# Usage:
#   ./scripts/backup-db.sh
#
# Restore (try it in the test database first, to check that the backup is good):
#   gunzip -c ~/PortfolioBossBackups/portfolioboss-YYYY-MM-DD.sql.gz | psql portfolioboss_test
#
# Requires: PostgreSQL running with the portfolioboss database (`brew services start postgresql@17`).

set -euo pipefail

BACKUP_DIR="${HOME}/PortfolioBossBackups"
BACKUP_FILE="${BACKUP_DIR}/portfolioboss-$(date +%F).sql.gz"

# postgresql@17 is "keg-only": Homebrew installs it but keeps pg_dump out of the PATH.
PG_DUMP="$(command -v pg_dump || echo "$(brew --prefix postgresql@17)/bin/pg_dump")"

mkdir -p "${BACKUP_DIR}"

# Written to a .partial file first: a failed dump must never leave a file that looks like a good backup,
# nor overwrite the good one from earlier today.
"${PG_DUMP}" portfolioboss | gzip > "${BACKUP_FILE}.partial"
mv "${BACKUP_FILE}.partial" "${BACKUP_FILE}"

echo "› backup written to ${BACKUP_FILE}"

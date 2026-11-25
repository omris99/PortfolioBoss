#!/usr/bin/env bash
# Builds the demo database for screenshots of every screen: portfolioboss_demo, a made-up portfolio of real
# US stocks (demo-data.sql) on top of the real database's market data — the scans and the stocks they found, the
# recommendations, the analyses, the daily closes, the analysts' price targets, Finnhub's answers and the Bank of
# Israel's rates. Of the portfolio itself nothing is read: no quantity, price paid, trade, cash, investor, alert or tax
# row. The holdings' tables are read only for the market data of four stocks (DEMO_STOCKS_FROM_THE_HOLDINGS): IB's
# contract and two words for the company, and the stocks' analyses.
#
#   scripts/demo/create-demo-db.sh      # drops portfolioboss_demo, if any, and builds it again
#
# Safety:
#   - it writes only to portfolioboss_demo: the name is fixed here and the script takes no arguments;
#   - it reads the real database only in read-only transactions (PostgreSQL itself refuses any write in them);
#   - it counts the rows of every table of the real database before and after, and fails if a count changed.
#
# Then run the app on the demo database, on a port where no TWS listens — so that Sync now can never bring the real
# account into it:
#   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/portfolioboss_demo ./run.sh 7599
# and once it serves, once: node scripts/demo/after-the-app-starts.mjs (the alerts and the confirmed tax days).
set -euo pipefail

readonly PG_BIN=/opt/homebrew/opt/postgresql@17/bin
readonly REAL_DATABASE=portfolioboss
readonly DEMO_DATABASE=portfolioboss_demo
readonly SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

# IB's contract ids of NVDA, AAPL, INTC and TTWO: stocks the scans never found, whose closes, targets, Finnhub answers
# and analyses the real database has from the holdings' syncs.
readonly DEMO_STOCKS_FROM_THE_HOLDINGS="4815747, 265598, 270639, 6478131"
readonly SPY_CON_ID=756733

if [[ $# -gt 0 ]]; then
  echo "[demo error] this script takes no arguments: it writes only to $DEMO_DATABASE" >&2
  exit 1
fi

read_real_database() {
  PGOPTIONS='-c default_transaction_read_only=on' "$PG_BIN/psql" -X -q -v ON_ERROR_STOP=1 -d "$REAL_DATABASE" "$@"
}

write_demo_database() {
  "$PG_BIN/psql" -X -q -v ON_ERROR_STOP=1 -d "$DEMO_DATABASE" "$@"
}

# Every table of the real database with its number of rows, one line each.
count_rows_of_real_database() {
  read_real_database -At -c "
    SELECT table_name || ' ' || (xpath('/row/c/text()', query_to_xml(
             format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    ORDER BY table_name"
}

# Copies the rows a query on the real database gives into a table of the demo database.
copy_from_real_database() {
  local table=$1 columns=$2 query=$3
  read_real_database -c "\\copy ($query) TO STDOUT" | write_demo_database -c "\\copy $table ($columns) FROM STDIN"
  echo "[demo] copied $(write_demo_database -At -c "SELECT count(*) FROM $table") rows into $table"
}

# Each id sequence just after the table's largest id: rows copied with their ids would collide with new ones otherwise.
move_id_sequences_past_the_copied_rows() {
  write_demo_database -c "
    DO \$\$
    DECLARE table_with_id record;
    BEGIN
      FOR table_with_id IN
        SELECT table_name FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'id' AND column_default LIKE 'nextval%'
      LOOP
        EXECUTE format('SELECT setval(pg_get_serial_sequence(%L, ''id''), coalesce((SELECT max(id) FROM %I), 0) + 1, false)',
                       table_with_id.table_name, table_with_id.table_name);
      END LOOP;
    END \$\$"
}

rows_before=$(count_rows_of_real_database)

echo "[demo] building $DEMO_DATABASE (the real database is only read)"
"$PG_BIN/dropdb" --if-exists "$DEMO_DATABASE"
"$PG_BIN/createdb" "$DEMO_DATABASE"

# The real database's tables without their rows, and Flyway's history, so that the app takes it as up to date.
PGOPTIONS='-c default_transaction_read_only=on' "$PG_BIN/pg_dump" --schema-only --no-owner --no-privileges \
  "$REAL_DATABASE" | write_demo_database > /dev/null
PGOPTIONS='-c default_transaction_read_only=on' "$PG_BIN/pg_dump" --data-only --table=flyway_schema_history \
  "$REAL_DATABASE" | write_demo_database > /dev/null

# The market data — the same for anyone who follows these stocks: nothing in it is about the portfolio.
copy_from_real_database scan "id, scanned_at, scan_codes" \
  "SELECT id, scanned_at, scan_codes FROM scan"
copy_from_real_database scan_candidate \
  "id, scan_id, con_id, symbol, company_name, exchange, industry, category, rank_by_scan_code, close_at_scan, close_date" \
  "SELECT id, scan_id, con_id, symbol, company_name, exchange, industry, category, rank_by_scan_code, close_at_scan, close_date FROM scan_candidate"
copy_from_real_database investment_recommendation "id, scan_id, recommended_at, picks" \
  "SELECT id, scan_id, recommended_at, picks FROM investment_recommendation"
copy_from_real_database stock_analysis \
  "id, scan_candidate_id, analyzed_at, model, result, input_tokens, output_tokens, tavily_credits, cost_usd, search_results, finnhub_stock" \
  "SELECT id, scan_candidate_id, analyzed_at, model, result, input_tokens, output_tokens, tavily_credits, cost_usd, search_results, finnhub_stock FROM stock_analysis WHERE scan_candidate_id IS NOT NULL"
copy_from_real_database daily_close "con_id, bar_date, close_price" \
  "SELECT con_id, bar_date, close_price FROM daily_close WHERE con_id IN ($SPY_CON_ID, $DEMO_STOCKS_FROM_THE_HOLDINGS) OR con_id IN (SELECT con_id FROM scan_candidate)"
copy_from_real_database analyst_price_target "con_id, published_at, firm, price_target, previous_price_target, headline" \
  "SELECT con_id, published_at, firm, price_target, previous_price_target, headline FROM analyst_price_target WHERE con_id IN ($DEMO_STOCKS_FROM_THE_HOLDINGS) OR con_id IN (SELECT con_id FROM scan_candidate)"
copy_from_real_database exchange_rate "currency, rate_date, rate" \
  "SELECT currency, rate_date, rate FROM exchange_rate"

# The four stocks' market data that sits in the holdings' tables, set aside for demo-data.sql, which drops it.
write_demo_database -c "
  CREATE SCHEMA demo_source;
  CREATE TABLE demo_source.stock_words (con_id integer, symbol varchar, sector varchar, industry varchar);
  CREATE TABLE demo_source.finnhub_answer (con_id integer, symbol varchar, finnhub_data jsonb);
  CREATE TABLE demo_source.latest_analysis (con_id integer, analyzed_at timestamptz, model varchar, result jsonb,
      input_tokens integer, output_tokens integer, tavily_credits integer, cost_usd numeric, search_results jsonb);"
copy_from_real_database demo_source.stock_words "con_id, symbol, sector, industry" \
  "SELECT DISTINCT ON (con_id) con_id, symbol, sector, industry FROM holding WHERE con_id IN ($DEMO_STOCKS_FROM_THE_HOLDINGS) ORDER BY con_id, last_synced_at DESC"
copy_from_real_database demo_source.finnhub_answer "con_id, symbol, finnhub_data" \
  "SELECT con_id, symbol, finnhub_data FROM stock WHERE con_id IN ($DEMO_STOCKS_FROM_THE_HOLDINGS)"
copy_from_real_database demo_source.latest_analysis \
  "con_id, analyzed_at, model, result, input_tokens, output_tokens, tavily_credits, cost_usd, search_results" \
  "SELECT DISTINCT ON (holding.con_id) holding.con_id, analysis.analyzed_at, analysis.model, analysis.result, analysis.input_tokens, analysis.output_tokens, analysis.tavily_credits, analysis.cost_usd, analysis.search_results FROM stock_analysis analysis JOIN holding ON holding.id = analysis.holding_id WHERE holding.con_id IN ($DEMO_STOCKS_FROM_THE_HOLDINGS) ORDER BY holding.con_id, analysis.analyzed_at DESC"
move_id_sequences_past_the_copied_rows

# The made-up portfolio, in one transaction.
write_demo_database -1 -f "$SCRIPT_DIR/demo-data.sql"
move_id_sequences_past_the_copied_rows
write_demo_database -At -c "
  SELECT '[demo] made up: ' || (SELECT count(*) FROM holding WHERE status = 'OPEN') || ' open holdings, '
      || (SELECT count(*) FROM holding WHERE status = 'CLOSED') || ' sold in full, '
      || (SELECT count(*) FROM trade) || ' trades, '
      || (SELECT count(*) FROM investor) || ' investors, '
      || (SELECT count(*) FROM flex_trade) || ' Flex trades'"

rows_after=$(count_rows_of_real_database)
if [[ "$rows_before" != "$rows_after" ]]; then
  echo "[demo error] the real database's row counts changed while the script ran (was the app syncing?):" >&2
  diff <(echo "$rows_before") <(echo "$rows_after") >&2 || true
  exit 1
fi
echo "[demo] the real database is unchanged: $(echo "$rows_before" | wc -l | tr -d ' ') tables, the same row counts"
echo "[demo] done. Run the app on it with:"
echo "  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/$DEMO_DATABASE ./run.sh 7599"

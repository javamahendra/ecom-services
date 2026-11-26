#!/bin/sh
set -eu

if ! printf '%s' "${MYSQL_EXPORTER_PASSWORD:-}" | grep -Eq '^[A-Za-z0-9_!@#%+=.-]+$'; then
  echo "MYSQL_EXPORTER_PASSWORD must contain only letters, numbers, and _!@#%+=.-" >&2
  exit 1
fi

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'prometheus'@'%' IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}';
ALTER USER 'prometheus'@'%' IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'prometheus'@'%';
SQL

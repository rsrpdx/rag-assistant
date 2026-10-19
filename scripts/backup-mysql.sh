#!/bin/bash
# MySQL Backup Script for RAG Assistant
# Usage: ./scripts/backup-mysql.sh
# Cron: 0 2 * * * /opt/rag-assistant/scripts/backup-mysql.sh >> /var/log/rag-backup.log 2>&1

BACKUP_DIR="/opt/rag-assistant/backups"
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/rag_assistant_$TIMESTAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

docker exec rag-mysql mysqldump \
  -u root -p"${MYSQL_ROOT_PASSWORD:-root123}" \
  --single-transaction \
  --routines \
  --triggers \
  --databases rag_assistant \
  | gzip > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
  echo "[$(date)] Backup success: $BACKUP_FILE"
else
  echo "[$(date)] Backup FAILED"
  exit 1
fi

find "$BACKUP_DIR" -name "rag_assistant_*.sql.gz" -mtime +$RETENTION_DAYS -delete
echo "[$(date)] Cleaned up backups older than $RETENTION_DAYS days"
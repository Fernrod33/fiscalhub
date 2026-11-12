#!/bin/sh
# Preparação idempotente do MinIO: bucket versionado, criptografado (SSE-S3) e um usuário só para ele.
set -eu
mc mb --ignore-existing "local/$S3_BUCKET"
mc version enable "local/$S3_BUCKET"
mc encrypt set sse-s3 "local/$S3_BUCKET"
cat > /tmp/politica.json <<JSON
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:*"],
 "Resource":["arn:aws:s3:::$S3_BUCKET","arn:aws:s3:::$S3_BUCKET/*"]}]}
JSON
mc admin policy create local fiscalhub-app /tmp/politica.json
mc admin user add local "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
mc admin policy attach local fiscalhub-app --user "$S3_ACCESS_KEY" 2>/dev/null || true # já anexada
echo "MinIO pronto: $S3_BUCKET versionado e com SSE"

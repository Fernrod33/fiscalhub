#!/bin/sh
# Preparação idempotente do MinIO: bucket versionado, criptografado (SSE-S3) e um usuário só com as operações de objeto
# e de versão desse bucket (sem mexer na política, no versionamento nem apagar o bucket).
set -eu
mc mb --ignore-existing "local/$S3_BUCKET"
mc version enable "local/$S3_BUCKET"
mc encrypt set sse-s3 "local/$S3_BUCKET"
# versão antiga (arquivo trocado ou excluído pelo usuário) recuperável por 30 dias, depois some (LGPD: retenção finita)
cat > /tmp/ciclo.json <<JSON
{"Rules":[{"ID":"versoes-antigas-30-dias","Status":"Enabled","Filter":{"Prefix":""},
 "NoncurrentVersionExpiration":{"NoncurrentDays":30}}]}
JSON
mc ilm rule import "local/$S3_BUCKET" < /tmp/ciclo.json
cat > /tmp/politica.json <<JSON
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:ListBucket","s3:ListBucketVersions","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::$S3_BUCKET"]},
 {"Effect":"Allow","Action":["s3:GetObject","s3:GetObjectVersion","s3:PutObject","s3:DeleteObject","s3:DeleteObjectVersion"],
  "Resource":["arn:aws:s3:::$S3_BUCKET/*"]}]}
JSON
mc admin policy create local fiscalhub-app /tmp/politica.json
mc admin user add local "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
mc admin policy attach local fiscalhub-app --user "$S3_ACCESS_KEY" 2>/dev/null || true # já anexada
echo "MinIO pronto: $S3_BUCKET versionado (versões antigas por 30 dias) e com SSE"

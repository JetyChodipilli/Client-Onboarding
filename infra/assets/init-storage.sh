#!/bin/sh
# Development/CI provisioning only. Production buckets and IAM are provisioned separately.
set -eu
mc alias set local http://storage:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/$ASSET_STORAGE_BUCKET"
mc version enable "local/$ASSET_STORAGE_BUCKET"
mc anonymous set none "local/$ASSET_STORAGE_BUCKET"
cat > /tmp/asset-policy.json <<EOF
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:GetBucketVersioning"],"Resource":["arn:aws:s3:::$ASSET_STORAGE_BUCKET"]},
 {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:GetObjectVersion"],"Resource":["arn:aws:s3:::$ASSET_STORAGE_BUCKET/*"]}
]}
EOF
mc admin user add local "$ASSET_STORAGE_ACCESS_KEY" "$ASSET_STORAGE_SECRET_KEY"
mc admin policy create local asset-app /tmp/asset-policy.json
mc admin policy attach local asset-app --user "$ASSET_STORAGE_ACCESS_KEY"

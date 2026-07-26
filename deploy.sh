#!/usr/bin/env bash
# Build the Lambda zip and deploy it. Extra args are passed to `terraform apply`
# (e.g. ./deploy.sh -auto-approve). One-time setup: see README "Deploy".
# After the first deploy, push the secrets with ./deploy-secrets.sh.
set -euo pipefail
cd "$(dirname "$0")"

./gradlew build
VERSION=$(./gradlew -q printVersion)

# load:
#   CHAT_MODEL_TYPE
#   CHUNKS_TO_PROVIDE
#   INGESTION_MANIFEST_BUCKET
#   VECTOR_STORE_BUCKET
#   INGESTION_MANIFEST_ID
#   TERRAFORM_TFSTATE_S3_BUCKET
#   TERRAFORM_TFSTATE_S3_REGION
#   DEPLOYMENT_REGION
#   LAMBDA_FUNCTION_NAME
# (OPEN_AI_API_KEY and SYMMETRIC_SIGNING_KEY are secrets, pushed via deploy-secrets.sh)
source local/config.sh

export TF_VAR_aws_lambda_function_name="${LAMBDA_FUNCTION_NAME}"
export TF_VAR_chat_model_type="${CHAT_MODEL_TYPE}"
export TF_VAR_vector_query_config="${VECTOR_QUERY_CONFIG}"
export TF_VAR_ingestion_manifest_bucket="${INGESTION_MANIFEST_BUCKET}"
export TF_VAR_vector_store_bucket="${VECTOR_STORE_BUCKET}"
export TF_VAR_ingestion_manifest_id="${INGESTION_MANIFEST_ID}"

# Skipped once initialized — if the backend or providers change, delete terraform/.terraform to re-init.
if [ ! -d terraform/.terraform ]; then
  terraform -chdir=terraform init -backend-config="bucket=${TERRAFORM_TFSTATE_S3_BUCKET}" -backend-config="region=${TERRAFORM_TFSTATE_S3_REGION}" -input=false
fi

terraform -chdir=terraform apply -var "app_version=$VERSION" -var "aws_region=$DEPLOYMENT_REGION" "$@"

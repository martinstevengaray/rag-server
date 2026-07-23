#!/usr/bin/env bash
# Push secrets from local/config.sh to SSM Parameter Store.
# Run ./deploy.sh first — terraform creates the (placeholder) parameters this fills in.
set -euo pipefail
cd "$(dirname "$0")"

source local/config.sh

# Push a secret value into an existing SSM SecureString parameter, only writing when the value changes.
push_secret() {
  local param_name="$1" value="$2"
  local current
  if ! current=$(aws ssm get-parameter --name "$param_name" --with-decryption \
    --region "$DEPLOYMENT_REGION" --query Parameter.Value --output text 2>/dev/null); then
    echo "Parameter $param_name not found — run ./deploy.sh first (terraform creates it)." >&2
    exit 1
  fi
  if [ "$current" = "$value" ]; then
    echo "$param_name already up to date."
    return
  fi
  aws ssm put-parameter --name "$param_name" --type SecureString --overwrite \
    --region "$DEPLOYMENT_REGION" --value "$value" > /dev/null
  echo "$param_name updated."
}

push_secret "/${LAMBDA_FUNCTION_NAME}/open-ai-api-key" "$OPEN_AI_API_KEY"
push_secret "/${LAMBDA_FUNCTION_NAME}/symmetric-signing-key" "$SYMMETRIC_SIGNING_KEY"

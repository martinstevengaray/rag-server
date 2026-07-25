provider "aws" {
  region = var.aws_region
}

locals {
  lambda_zip = "${path.module}/../build/distributions/rag-server-lambda-${var.app_version}.zip"
}

# Latest AWS Parameters and Secrets Lambda Extension layer for this region,
# published by AWS as a public parameter (x86_64 variant). The function code
# reads SSM parameters over localhost HTTP through this layer
# (see AwsServicesDelegate.fetchSmmParameterValue).
data "aws_ssm_parameter" "secrets_extension" {
  name = "/aws/service/aws-parameters-and-secrets-lambda-extension/x86/latest"
}

# SecureString parameters use the account's AWS-managed SSM key.
data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

# Terraform owns these parameters' existence, not their values: the real secrets
# are pushed out-of-band by ./deploy-secrets.sh so they never enter terraform state.
#   aws ssm put-parameter --name <name> --type SecureString --overwrite --value <secret>
resource "aws_ssm_parameter" "open_ai_api_key" {
  name  = "/${var.aws_lambda_function_name}/open-ai-api-key"
  type  = "SecureString"
  value = "placeholder - set the real value with aws ssm put-parameter"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "symmetric_signing_key" {
  name  = "/${var.aws_lambda_function_name}/symmetric-signing-key"
  type  = "SecureString"
  value = "placeholder - set the real value with aws ssm put-parameter"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_iam_role" "lambda" {
  name = "${var.aws_lambda_function_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "basic_execution" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "read_secrets" {
  name = "${var.aws_lambda_function_name}-read-secrets"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "ssm:GetParameter"
        Resource = [
          aws_ssm_parameter.open_ai_api_key.arn,
          aws_ssm_parameter.symmetric_signing_key.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = data.aws_kms_alias.ssm.target_key_arn
      }
    ]
  })
}

# S3 access for the Lambda (see S3Utils). Scoped to actions rather than to
# specific buckets, so any bucket in the account is reachable.
resource "aws_iam_role_policy" "s3_access" {
  name = "${var.aws_lambda_function_name}-s3-access"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = "arn:aws:s3:::*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "arn:aws:s3:::*/*"
      }
    ]
  })
}

resource "aws_lambda_function" "this" {
  function_name = var.aws_lambda_function_name
  role          = aws_iam_role.lambda.arn
  runtime       = "java21"
  handler       = "com.mgaray.ragserver.server.JavaLambdaServer::handleRequest"

  filename         = local.lambda_zip
  source_code_hash = filebase64sha256(local.lambda_zip)

  # RAG startup loads langchain4j and reads from S3, so give it headroom.
  memory_size = 1024
  timeout     = 60

  # Serves SSM parameters to the function over localhost HTTP (with caching),
  # so the code needs no AWS SDK to read secrets.
  layers = [data.aws_ssm_parameter.secrets_extension.insecure_value]

  environment {
    variables = {
      OPEN_AI_API_KEY_SSM_PARAMETER_KEY       = aws_ssm_parameter.open_ai_api_key.name
      SYMMETRIC_SIGNING_KEY_SSM_PARAMETER_KEY = aws_ssm_parameter.symmetric_signing_key.name
      CHAT_MODEL_TYPE                         = var.chat_model_type
      CHUNKS_TO_PROVIDE                       = var.chunks_to_provide
      INGESTION_MANIFEST_BUCKET               = var.ingestion_manifest_bucket
      VECTOR_STORE_BUCKET                     = var.vector_store_bucket
      INGESTION_MANIFEST_ID                   = var.ingestion_manifest_id
    }
  }
}

resource "aws_lambda_function_url" "this" {
  function_name      = aws_lambda_function.this.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = var.aws_lambda_cors_allow_origins
    allow_methods = ["*"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 3600
  }
}

resource "aws_lambda_permission" "public_url" {
  statement_id           = "AllowPublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.this.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

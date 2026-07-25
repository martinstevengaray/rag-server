provider "aws" {
  region = var.aws_region
}

locals {
  lambda_zip = "${path.module}/../build/distributions/rag-server-lambda-${var.app_version}.zip"

  vector_bucket_arn = "arn:aws:s3vectors:${var.aws_region}:${data.aws_caller_identity.current.account_id}:bucket/${var.vector_store_bucket}"
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

data "aws_caller_identity" "current" {}

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

# S3 Vectors is a separate service from S3, with its own "s3vectors:" actions and
# ARN namespace, so the s3_access policy above does not reach it (see S3VectorStore).
# The Lambda only queries; writes happen during ingestion, which runs outside Lambda.
resource "aws_iam_role_policy" "s3_vectors_read" {
  name = "${var.aws_lambda_function_name}-s3-vectors-read"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3vectors:GetVectorBucket", "s3vectors:ListIndexes"]
        Resource = local.vector_bucket_arn
      },
      {
        Effect = "Allow"
        Action = [
          "s3vectors:GetIndex",
          "s3vectors:QueryVectors",
          "s3vectors:GetVectors",
          "s3vectors:ListVectors"
        ]
        Resource = "${local.vector_bucket_arn}/index/*"
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

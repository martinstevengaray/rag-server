variable "app_version" {
  description = "App version from the Gradle build (single source of truth in build.gradle). Pass it through: terraform apply -var \"app_version=$(cd .. && ./gradlew -q printVersion)\""
  type        = string
}

variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
}

variable "aws_lambda_function_name" {
  description = "Name of the Lambda function"
  type        = string
}

# --- Application configuration (plain environment variables) ---

variable "chat_model_type" {
  description = "CHAT_MODEL_TYPE env var passed to the Lambda"
  type        = string
}

variable "vector_query_config" {
  description = "VECTOR_QUERY_CONFIG env var: number of retrieved chunks handed to the model"
  type        = string
}

variable "ingestion_manifest_bucket" {
  description = "S3 bucket holding the ingestion manifest the Lambda reads on startup"
  type        = string
}

variable "vector_store_bucket" {
  description = "S3 bucket holding the vector store the Lambda reads/writes"
  type        = string
}

variable "ingestion_manifest_id" {
  description = "INGESTION_MANIFEST_ID env var: which ingestion manifest to load"
  type        = string
}

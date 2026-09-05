# Beacon notifications target. Stub. See README.md: this is the landing zone shape for the
# notification fan-out that beacon-notifications (Spring Boot, on OpenShift) will eventually hand
# off to. There is no application code; the deployment package is a placeholder handler that logs
# the event and returns 200 so the SNS subscription can be wired up and tested end to end.

locals {
  mandatory_tags = {
    "meridian:app-id"              = "APP-13300"
    "meridian:owner"               = "beacon"
    "meridian:cost-centre"         = "CC-4471"
    "meridian:data-classification" = "internal"
    "meridian:environment"         = var.environment
    "meridian:managed-by"          = "terraform/platform-tooling"
  }
  tags = merge(local.mandatory_tags, var.tags)
}

data "archive_file" "placeholder" {
  type        = "zip"
  source_dir  = "${path.module}/src"
  output_path = "${path.module}/.build/placeholder.zip"
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  name                 = "${var.name}-role"
  assume_role_policy   = data.aws_iam_policy_document.assume.json
  permissions_boundary = "arn:aws:iam::000000000000:policy/CHANGEME-meridian-permissions-boundary"
  tags                 = local.tags
}

data "aws_iam_policy_document" "logs" {
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.lambda.arn}:*"]
  }
  statement {
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [var.kms_key_arn]
  }
  # Secrets Manager read is deliberately absent. When it is added, the entry is fed from Vault
  # (platform-tooling/vault/README.md), not created here.
}

resource "aws_iam_role_policy" "logs" {
  name   = "${var.name}-logs"
  role   = aws_iam_role.lambda.id
  policy = data.aws_iam_policy_document.logs.json
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.name}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
  tags              = local.tags
}

resource "aws_lambda_function" "this" {
  function_name    = var.name
  role             = aws_iam_role.lambda.arn
  runtime          = var.runtime
  handler          = var.handler
  filename         = data.archive_file.placeholder.output_path
  source_code_hash = data.archive_file.placeholder.output_base64sha256
  memory_size      = var.memory_size
  timeout          = var.timeout
  kms_key_arn      = var.kms_key_arn
  architectures    = ["arm64"]

  environment {
    variables = {
      MERIDIAN_ENVIRONMENT = var.environment
      LOG_LEVEL            = "info"
      # Placeholder. The real value comes from Vault -> Secrets Manager sync (BCN-233).
      NOTIFICATIONS_API_KEY_REF = "CHANGEME-secretsmanager-arn"
    }
  }

  dynamic "vpc_config" {
    for_each = length(var.vpc_subnet_ids) > 0 ? [1] : []
    content {
      subnet_ids         = var.vpc_subnet_ids
      security_group_ids = var.vpc_security_group_ids
    }
  }

  tracing_config {
    mode = "Active"
  }

  tags = local.tags

  depends_on = [aws_cloudwatch_log_group.lambda]
}

resource "aws_sns_topic" "notifications" {
  name              = "${var.name}-events"
  kms_master_key_id = var.kms_key_arn
  tags              = local.tags
}

resource "aws_sns_topic_subscription" "lambda" {
  topic_arn = aws_sns_topic.notifications.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.this.arn
}

resource "aws_lambda_permission" "sns" {
  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.this.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.notifications.arn
}

resource "aws_sqs_queue" "dlq" {
  name                      = "${var.name}-dlq"
  message_retention_seconds = 1209600
  kms_master_key_id         = var.kms_key_arn
  tags                      = local.tags
}

resource "aws_lambda_function_event_invoke_config" "this" {
  function_name          = aws_lambda_function.this.function_name
  maximum_retry_attempts = 2
  destination_config {
    on_failure {
      destination = aws_sqs_queue.dlq.arn
    }
  }
}

output "function_arn" {
  value = aws_lambda_function.this.arn
}

output "topic_arn" {
  description = "Publish target for beacon-notifications once BCN-240 wires the OpenShift side."
  value       = aws_sns_topic.notifications.arn
}

output "dlq_url" {
  value = aws_sqs_queue.dlq.url
}

output "role_arn" {
  value = aws_iam_role.lambda.arn
}

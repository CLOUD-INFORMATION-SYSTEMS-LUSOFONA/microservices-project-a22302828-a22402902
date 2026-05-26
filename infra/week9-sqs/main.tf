# trigger terraform workflow
terraform {
  required_version = ">= 1.3.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

provider "aws" {
  # Region from AWS_REGION or provider config; override with -var or TF_VAR_ as needed.
}

resource "aws_sqs_queue" "product_events_dlq" {
  name                      = "${var.name_prefix}-product-events-dlq"
  message_retention_seconds = 1209600 # 14 days
  tags                      = var.tags
}

resource "aws_sqs_queue" "product_events" {
  name                       = "${var.name_prefix}-product-events"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600 # 4 days
  receive_wait_time_seconds  = 20     # long polling (matches app default)

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.product_events_dlq.arn
    maxReceiveCount     = var.max_receive_count_before_dlq
  })

  tags = var.tags
}

resource "aws_sqs_queue" "product_events_fifo" {
  name                        = "cn-course-product-events.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
}

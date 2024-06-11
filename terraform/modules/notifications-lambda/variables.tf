variable "name" {
  description = "Function name. Bank naming standard: mtb-<env>-<domain>-<purpose>."
  type        = string
  default     = "mtb-sbx-beacon-notifications-target"
}

variable "environment" {
  description = "sbx, dev, uat or prod. Only sbx exists as of 2024-11 (BCN-201)."
  type        = string
  default     = "sbx"
  validation {
    condition     = contains(["sbx", "dev", "uat", "prod"], var.environment)
    error_message = "environment must be one of sbx, dev, uat, prod."
  }
}

variable "runtime" {
  description = "Lambda runtime. nodejs18.x to match the Node services; nodejs20.x when platform-services moves."
  type        = string
  default     = "nodejs18.x"
}

variable "handler" {
  type    = string
  default = "index.handler"
}

variable "memory_size" {
  type    = number
  default = 256
}

variable "timeout" {
  type    = number
  default = 30
}

variable "log_retention_days" {
  description = "CloudWatch retention. Bank minimum is 400 days for anything that might carry customer data (GIS-STD-009)."
  type        = number
  default     = 400
}

variable "kms_key_arn" {
  description = "CMK for environment variables and logs. Provided by the landing zone; CHANGEME until BCN-207 lands."
  type        = string
  default     = "arn:aws:kms:us-east-1:000000000000:key/CHANGEME-beacon-cmk-id"
}

variable "vpc_subnet_ids" {
  description = "Private subnets from the landing zone. Empty list means no VPC config (sandbox only)."
  type        = list(string)
  default     = []
}

variable "vpc_security_group_ids" {
  type    = list(string)
  default = []
}

variable "tags" {
  description = "Merged with the mandatory tag set from GIS-STD-040."
  type        = map(string)
  default     = {}
}

terraform {
  # 이 Terraform 설정을 실행할 수 있는 최소 Terraform CLI 버전이다.
  required_version = ">= 1.6.0"

  # AWS 리소스를 만들기 위해 HashiCorp 공식 AWS provider를 사용한다.
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# 실제 리소스를 생성할 AWS 리전은 terraform.tfvars의 aws_region 값으로 결정된다.
provider "aws" {
  region = var.aws_region
}

variable "project_name" {
  # 리소스 이름과 태그에 공통으로 들어가는 프로젝트 이름이다.
  description = "AWS 리소스 이름과 태그에 사용할 프로젝트 이름"
  type        = string
  default     = "catcheat"
}

variable "environment" {
  # prod, dev 같은 배포 환경 이름이다. 리소스 이름 prefix와 태그에 사용된다.
  description = "배포 환경 이름"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  # 현재 프로젝트는 서울 리전(ap-northeast-2)을 기본값으로 사용한다.
  description = "AWS 리소스를 생성할 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  # 백엔드 서버로 사용할 EC2 인스턴스 크기다.
  description = "백엔드 서버 EC2 인스턴스 타입"
  type        = string
  default     = "t3.micro"
}

variable "key_pair_name" {
  # SSH 접속에 사용할 기존 EC2 Key Pair 이름이다. AWS 콘솔의 EC2 > Key pairs에서 확인한다.
  description = "SSH 접속에 사용할 기존 EC2 키페어 이름"
  type        = string
  default     = null
}

variable "ssh_allowed_cidr" {
  # SSH를 허용할 IP 범위다. 보안상 본인 공인 IP 하나만 /32로 여는 것을 권장한다.
  description = "EC2 SSH 접속을 허용할 CIDR"
  type        = string
}

variable "app_allowed_cidr" {
  # 80과 app_port 접근을 허용할 IP 범위다. 초기 테스트는 전체 공개, 운영에서는 ALB 등으로 좁힌다.
  description = "HTTP 및 백엔드 앱 포트 접근을 허용할 CIDR"
  type        = string
  default     = "0.0.0.0/0"
}

variable "app_port" {
  # Spring Boot 서버가 노출될 포트다. application.yml의 SERVER_PORT 기본값과 맞춘다.
  description = "백엔드 애플리케이션 포트"
  type        = number
  default     = 8080
}

variable "s3_bucket_name" {
  # 백엔드가 presigned URL 생성, 업로드, 다운로드, 삭제에 사용할 기존 S3 버킷 이름이다.
  description = "백엔드가 사용할 기존 S3 버킷 이름"
  type        = string
}

variable "root_volume_size_gb" {
  # EC2 루트 디스크 크기다. Docker 이미지와 로그가 쌓일 수 있어 MVP 기본값은 20GiB로 둔다.
  description = "EC2 루트 EBS 볼륨 크기"
  type        = number
  default     = 20
}

variable "common_tags" {
  # 모든 주요 리소스에 추가로 붙일 공통 태그다. 비용 추적이나 소유자 표시가 필요할 때 사용한다.
  description = "지원되는 모든 리소스에 추가할 공통 태그"
  type        = map(string)
  default     = {}
}

# CatchEat 전용 VPC의 전체 IP 대역
variable "vpc_cidr" {
  description = "CatchEat 전용 VPC CIDR"
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Public Subnet CIDR 목록"
  type        = list(string)
  default     = ["10.20.1.0/24", "10.20.2.0/24"]
}
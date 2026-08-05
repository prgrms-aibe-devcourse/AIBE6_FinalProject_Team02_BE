locals {
  # 리소스 이름을 catcheat-prod-* 형태로 통일하기 위한 prefix다.
  name_prefix = "${var.project_name}-${var.environment}"

  # AWS 콘솔과 비용 탐색기에서 리소스를 식별하기 쉽게 공통 태그를 붙인다.
  tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    },
    var.common_tags
  )
}


# EC2에 사용할 최신 Ubuntu 24.04 LTS AMI를 Canonical 공식 계정에서 조회한다.
data "aws_ami" "ubuntu_2404" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# 백엔드 EC2에 붙일 보안그룹이다.
resource "aws_security_group" "backend" {
  name        = "${local.name_prefix}-backend-sg"
  description = "Security group for the CatchEat backend host"
  vpc_id      = aws_vpc.main.id

  # SSH는 terraform.tfvars의 ssh_allowed_cidr에서만 허용한다.
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  # HTTP 80 포트다. 나중에 ALB/HTTPS를 붙이면 이 경로가 주 진입점이 된다.
  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.app_allowed_cidr]
  }

  # 초기 테스트용 Spring Boot 직접 접근 포트다. 운영 단계에서는 ALB 뒤로 숨기는 것이 좋다.
  ingress {
    description = "Spring Boot app"
    from_port   = var.app_port
    to_port     = var.app_port
    protocol    = "tcp"
    cidr_blocks = [var.app_allowed_cidr]
  }

  # EC2가 패키지 설치, Docker 이미지 다운로드, 외부 API 호출을 할 수 있도록 outbound는 전체 허용한다.
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-backend-sg"
  })
}

# EC2가 AWS 서비스를 호출할 수 있도록 붙일 IAM Role이다.
resource "aws_iam_role" "backend" {
  name = "${local.name_prefix}-backend-role"

  # EC2 서비스가 이 Role을 AssumeRole 할 수 있게 허용한다.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.tags
}

# 백엔드가 기존 S3 버킷에 접근하기 위한 최소 권한 정책이다.
resource "aws_iam_role_policy" "backend_s3" {
  name = "${local.name_prefix}-s3-access"
  role = aws_iam_role.backend.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 버킷 목록 조회 권한이다.
        Effect = "Allow"
        Action = [
          "s3:ListBucket"
        ]
        Resource = "arn:aws:s3:::${var.s3_bucket_name}"
      },
      {
        # 객체 다운로드, 업로드, 삭제 권한이다. presigned URL 발급과 사용자 사진 삭제에 필요하다.
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "arn:aws:s3:::${var.s3_bucket_name}/*"
      }
    ]
  })
}

# IAM Role을 EC2에 붙이기 위한 Instance Profile이다.
resource "aws_iam_instance_profile" "backend" {
  name = "${local.name_prefix}-backend-profile"
  role = aws_iam_role.backend.name
}

# 실제 백엔드 서버로 사용할 EC2 인스턴스다.
resource "aws_instance" "backend" {
  ami                         = data.aws_ami.ubuntu_2404.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.backend.id]
  iam_instance_profile        = aws_iam_instance_profile.backend.name
  key_name                    = var.key_pair_name
  associate_public_ip_address = true
  # 첫 부팅 때 Docker와 Docker Compose plugin을 설치한다.
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {})

  metadata_options {
    http_endpoint = "enabled"
    http_tokens = "required"
    http_put_response_hop_limit = 2
  }

  # 루트 디스크는 gp3, 암호화 활성화로 생성한다.
  root_block_device {
    volume_size = var.root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-backend"
  })
}

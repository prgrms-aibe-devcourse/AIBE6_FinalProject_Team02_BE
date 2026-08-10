output "backend_instance_id" {
  # AWS 콘솔이나 CLI에서 EC2를 식별할 때 사용하는 인스턴스 ID다.
  description = "백엔드 EC2 인스턴스 ID"
  value       = aws_instance.backend.id
}

output "backend_public_ip" {
  # SSH 접속과 초기 HTTP 테스트에 사용할 public IP다.
  description = "백엔드 EC2 public IP"
  value       = aws_eip.backend.public_ip
}


output "backend_test_url" {
  # ALB/도메인 연결 전, 8080 포트로 직접 테스트할 URL이다.
  description = "백엔드 앱 직접 접속 URL [초기 배포 검증용 HTTP URL 이 운영환경에서는 사용하지 않음]"
  value       = "http://${aws_eip.backend.public_ip}:${var.app_port}"
}

output "security_group_id" {
  # 백엔드 EC2에 연결된 보안그룹 ID다.
  description = "백엔드 EC2 보안그룹 ID"
  value       = aws_security_group.backend.id
}


output "vpc_id" {
  description = "CatchEat 전용 VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "CatchEat public subnet ID 목록"
  value       = aws_subnet.public[*].id
}

output "ecr_repository_url" {
  description = "백엔드 도커 이미지를 저장하는 ECR 리포지토리 URL"
  value       = aws_ecr_repository.backend.repository_url
}


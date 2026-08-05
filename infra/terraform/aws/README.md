# CatchEat AWS Terraform

This is the first MVP infrastructure layer:

- One EC2 host in the default VPC
- Security group for SSH, HTTP, and the Spring Boot app port
- IAM role/profile for S3 access to the existing CatchEat bucket
- User data that installs Docker and Docker Compose

It intentionally does not put application secrets into Terraform state. Keep `.env` on the server or move secrets to SSM/Secrets Manager in the next step.

## Usage

```bash
cd infra/terraform/aws
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`, especially:

- `key_pair_name`
- `ssh_allowed_cidr`
- `s3_bucket_name`

Then run:

```bash
terraform init
terraform plan
terraform apply
```

## Notes

- The current app still needs a deployment path after EC2 is created.
- For the next step, add either a backend Dockerfile/Compose deployment or move the service to ECS.
- For production-grade deployment, add RDS PostgreSQL, HTTPS, domain routing, and secret storage.

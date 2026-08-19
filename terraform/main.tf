terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "sa-east-1" # mesma região usada no docker-compose local, por consistência
}

# Reaproveita a VPC padrão — evita provisionar rede nova só pra esse projeto.
# Numa conta de produção real, isso seria uma VPC dedicada com subnets
# públicas/privadas separadas (ver nota em network.tf sobre isso).
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

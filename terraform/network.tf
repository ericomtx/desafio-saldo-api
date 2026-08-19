# O ALB fica INTERNO (não exposto direto à internet) — quem recebe tráfego
# público é o API Gateway (ver api_gateway.tf), que fala com o ALB por
# dentro da VPC via VPC Link. Isso evita expor o ALB publicamente à toa,
# já que o API Gateway já cobre autenticação/throttling na borda.

resource "aws_security_group" "alb" {
  name        = "saldo-api-alb-sg"
  description = "Permite tráfego do VPC Link do API Gateway até o ALB"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "API Gateway VPC Link"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block] # só de dentro da própria VPC
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "web_task" {
  name        = "saldo-api-web-sg"
  description = "Permite tráfego do ALB até as tasks da API"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Do ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id] # só do ALB, não do mundo
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] # precisa alcançar DynamoDB
  }
}

resource "aws_security_group" "consumer_task" {
  name        = "saldo-api-consumer-sg"
  description = "Consumer não recebe tráfego de entrada nenhum — só sai pra SQS/DynamoDB"
  vpc_id      = data.aws_vpc.default.id

  # Sem ingress de propósito — nada nunca inicia conexão COM o consumer.

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "vpc_link" {
  name        = "saldo-api-vpc-link-sg"
  description = "Security group do VPC Link do API Gateway"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

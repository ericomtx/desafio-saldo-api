# Um repositório por serviço — a API e o consumer são deployados
# independentemente (task definitions e services separados), então cada um
# tem seu próprio ciclo de vida de imagem.

resource "aws_ecr_repository" "api" {
  name         = "saldo-api-web"
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "consumer" {
  name         = "saldo-api-consumer"
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

output "ecr_api_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecr_consumer_url" {
  value = aws_ecr_repository.consumer.repository_url
}

resource "aws_dynamodb_table" "saldos" {
  name         = "saldos"
  billing_mode = "PAY_PER_REQUEST" # tráfego em pico (2000/s) intercalado com vales — evita pagar capacidade ociosa
  hash_key     = "accountId"

  attribute {
    name = "accountId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "transacoes_processadas" {
  name         = "transacoes-processadas"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "transactionId"

  attribute {
    name = "transactionId"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt" # ADR 0003 — expira em 24h, não cresce indefinidamente
    enabled        = true
  }
}

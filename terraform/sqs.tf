# NOTA: em uma organização real, essa fila provavelmente pertenceria ao
# time do "Autorizador de transações" (o publicador), não ao time da API
# de saldo — nesse caso, o correto seria referenciar via `data
# "aws_sqs_queue"` em vez de criar. Está criada aqui por completude, já que
# o desafio a trata como parte do escopo.

resource "aws_sqs_queue" "dlq" {
  name                      = "transacoes-financeiras-processadas-dlq"
  message_retention_seconds = 1209600 # 14 dias — tempo maior que a fila principal, pra dar tempo de investigar
}

resource "aws_sqs_queue" "transacoes_financeiras" {
  name                       = "transacoes-financeiras-processadas"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600 # 4 dias, igual ao ambiente local

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount      = 5 # ADR 0005 — depois de 5 tentativas falhas, vai pra DLQ
  })
}

# --- Execution role (compartilhada) ---
# Usada pelo ECS pra puxar a imagem do ECR e escrever logs no CloudWatch —
# não é a identidade da APLICAÇÃO, é a identidade da infraestrutura que
# sobe o container. Igual pros dois serviços.

resource "aws_iam_role" "ecs_execution" {
  name = "saldo-api-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# --- Task role da API (web) ---
# Só leitura na tabela de saldos. Não toca na fila, não toca na tabela de
# dedup, não tem CreateTable/ListTables — least privilege de verdade.

resource "aws_iam_role" "web_task" {
  name = "saldo-api-web-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "web_task" {
  name = "saldo-api-web-dynamodb-read"
  role = aws_iam_role.web_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["dynamodb:GetItem"]
      Resource = aws_dynamodb_table.saldos.arn
    }]
  })
}

# --- Task role do consumer ---
# Escreve na tabela de saldos e na de dedup, consome/deleta da fila. Não
# tem acesso de leitura formatado pra API (não precisa, só escreve).

resource "aws_iam_role" "consumer_task" {
  name = "saldo-api-consumer-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "consumer_task" {
  name = "saldo-api-consumer-permissions"
  role = aws_iam_role.consumer_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["dynamodb:UpdateItem", "dynamodb:GetItem"]
        Resource = aws_dynamodb_table.saldos.arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:PutItem", "dynamodb:GetItem"]
        Resource = aws_dynamodb_table.transacoes_processadas.arn
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueUrl",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.transacoes_financeiras.arn
      }
    ]
  })
}

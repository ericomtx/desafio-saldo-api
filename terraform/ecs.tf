resource "aws_ecs_cluster" "this" {
  name = "saldo-api-cluster"
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/ecs/saldo-api-web"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "consumer" {
  name              = "/ecs/saldo-api-consumer"
  retention_in_days = 14
}

# --- Task definition: web (API) ---

resource "aws_ecs_task_definition" "web" {
  family                   = "saldo-api-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn             = aws_iam_role.web_task.arn

  container_definitions = jsonencode([{
    name      = "saldo-api-web"
    image     = "${aws_ecr_repository.api.repository_url}:latest"
    essential = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "APP_AWS_REGION", value = "sa-east-1" },
      { name = "APP_AWS_BOOTSTRAP_TABLES", value = "false" }
      # Propositalmente SEM app.aws.access-key/secret-key/endpoint-override:
      # em branco faz o AwsClientsConfig usar a IAM Role da task e os
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.web.name
        "awslogs-region"        = "sa-east-1"
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "web" {
  name            = "saldo-api-web-service"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = 2 # >1 desde já — a API recebe tráfego de usuário, precisa de HA mínima
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.web_task.id]
    assign_public_ip = true # subnets públicas reaproveitadas (ver main.tf) — sem isso a task não baixa a imagem do ECR
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name    = "saldo-api-web"
    container_port    = 8080
  }

  depends_on = [aws_lb_listener.web]
}

# --- Task definition: consumer ---

resource "aws_ecs_task_definition" "consumer" {
  family                   = "saldo-api-consumer"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn             = aws_iam_role.consumer_task.arn

  container_definitions = jsonencode([{
    name      = "saldo-api-consumer"
    image     = "${aws_ecr_repository.consumer.repository_url}:latest"
    essential = true
    environment = [
      { name = "APP_AWS_REGION", value = "sa-east-1" },
      { name = "APP_SQS_QUEUE_NAME", value = aws_sqs_queue.transacoes_financeiras.name },
      { name = "APP_AWS_BOOTSTRAP_TABLES", value = "false" }
      # Mesma lógica do web: sem credenciais/endpoint hardcoded.
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.consumer.name
        "awslogs-region"        = "sa-east-1"
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "consumer" {
  name            = "saldo-api-consumer-service"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.consumer.arn
  desired_count   = 2 # ponto de partida — Auto Scaling (ver autoscaling.tf) ajusta pra cima/baixo pela fila
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.consumer_task.id]
    assign_public_ip = true
  }
}

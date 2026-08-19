# --- Auto Scaling da API (web) — por CPU ---
# Target tracking é o padrão mais simples e mais comum: "mantenha a CPU
# média em X%", a AWS calcula sozinha quantas tasks são necessárias.

resource "aws_appautoscaling_target" "web" {
  max_capacity       = 10
  min_capacity       = 2
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.web.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "web_cpu" {
  name               = "saldo-api-web-cpu-target-tracking"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.web.resource_id
  scalable_dimension = aws_appautoscaling_target.web.scalable_dimension
  service_namespace  = aws_appautoscaling_target.web.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60
    scale_in_cooldown  = 120 # espera 2min antes de reduzir — evita oscilar (scale in/out repetido)
    scale_out_cooldown = 30  # reage rápido a um pico de tráfego
  }
}

# --- Auto Scaling do consumer — pela profundidade da fila (ADR 0006) ---
# Não existe uma métrica "SQS depth" predefinida pro Application Auto
# Scaling ECS, então usamos step scaling orientado por alarmes CloudWatch
# na métrica ApproximateNumberOfMessagesVisible da fila — mais direto de
# entender que uma métrica customizada com expressão matemática.

resource "aws_appautoscaling_target" "consumer" {
  max_capacity       = 20 # mais teto que a API — é aqui que o pico de 2000 msg/s realmente pressiona
  min_capacity       = 2
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.consumer.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "consumer_scale_out" {
  name               = "saldo-api-consumer-scale-out"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.consumer.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Average"

    step_adjustment {
      scaling_adjustment          = 4
      metric_interval_lower_bound = 0
    }
  }
}

resource "aws_appautoscaling_policy" "consumer_scale_in" {
  name               = "saldo-api-consumer-scale-in"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.consumer.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 120
    metric_aggregation_type = "Average"

    step_adjustment {
      scaling_adjustment          = -2
      metric_interval_upper_bound = 0
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "queue_depth_high" {
  alarm_name          = "saldo-api-queue-depth-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods   = 2
  metric_name          = "ApproximateNumberOfMessagesVisible"
  namespace            = "AWS/SQS"
  period               = 60
  statistic            = "Average"
  threshold            = 1000 # fila acumulando — sinal de que o consumer não está dando conta
  dimensions = {
    QueueName = aws_sqs_queue.transacoes_financeiras.name
  }
  alarm_actions = [aws_appautoscaling_policy.consumer_scale_out.arn]
}

resource "aws_cloudwatch_metric_alarm" "queue_depth_low" {
  alarm_name          = "saldo-api-queue-depth-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods   = 3
  metric_name          = "ApproximateNumberOfMessagesVisible"
  namespace            = "AWS/SQS"
  period               = 60
  statistic            = "Average"
  threshold            = 50 # fila praticamente vazia — pode reduzir capacidade
  dimensions = {
    QueueName = aws_sqs_queue.transacoes_financeiras.name
  }
  alarm_actions = [aws_appautoscaling_policy.consumer_scale_in.arn]
}

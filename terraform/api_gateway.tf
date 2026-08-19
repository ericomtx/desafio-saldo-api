# API Gateway HTTP API (mais simples/barato que REST API) na frente do ALB
# interno, conectado via VPC Link — permite manter o ALB fora da internet
# enquanto ainda expõe a API publicamente, com throttling/logging na borda.

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "saldo-api-vpc-link"
  security_group_ids = [aws_security_group.vpc_link.id]
  subnet_ids         = data.aws_subnets.default.ids
}

resource "aws_apigatewayv2_api" "this" {
  name          = "saldo-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "alb" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_uri    = aws_lb_listener.web.arn
  integration_method = "ANY"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.this.id
}

resource "aws_apigatewayv2_route" "balances" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "GET /balances/{accountId}"
  target    = "integrations/${aws_apigatewayv2_integration.alb.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  # Throttling na borda — protege o backend de picos abusivos, separado do
  # scaling normal do ECS.
  default_route_settings {
    throttling_burst_limit = 500
    throttling_rate_limit  = 200
  }
}

output "api_url" {
  value = aws_apigatewayv2_api.this.api_endpoint
}

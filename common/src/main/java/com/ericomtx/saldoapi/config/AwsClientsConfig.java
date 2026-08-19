package com.ericomtx.saldoapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

/**
 * Em dev local (contra LocalStack): `app.aws.access-key`/`secret-key` e
 * `endpoint-override` vêm preenchidos no application.yml, então usamos
 * credenciais estáticas e forçamos o endpoint local.
 *
 * Em produção (ECS): essas propriedades NÃO são definidas nas task
 * definitions (ver terraform/ecs.tf) — ficam em branco de propósito, então
 * o SDK cai no DefaultCredentialsProvider, que descobre automaticamente as
 * credenciais temporárias da IAM Role da task via o endpoint de metadados
 * do ECS, e usa os endpoints reais da AWS (sem endpointOverride nenhum).
 */
@Configuration
public class AwsClientsConfig {

    @Value("${app.aws.endpoint-override:}")
    String endpointOverride;

    @Value("${app.aws.region}")
    String region;

    @Value("${app.aws.access-key:}")
    String accessKey;

    @Value("${app.aws.secret-key:}")
    String secretKey;

    private AwsCredentialsProvider credentials() {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return DefaultCredentialsProvider.create(); // produção: usa a IAM Role da task
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials());
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride)); // só em dev local
        }
        return builder.build();
    }

    @Bean
    public SqsClient sqsClient() {
        SqsClientBuilder builder = SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials());
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride)); // só em dev local
        }
        return builder.build();
    }
}

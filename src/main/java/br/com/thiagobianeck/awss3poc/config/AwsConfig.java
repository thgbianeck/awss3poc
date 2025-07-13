package br.com.thiagobianeck.awss3poc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Configuração do AWS SDK para integração com S3
 * Suporta tanto LocalStack quanto AWS real
 *
 * @author Bianeck
 */
@Configuration
public class AwsConfig {

    @Value("${aws.s3.endpoint}")
    private String s3Endpoint;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.path-style-access:true}")
    private boolean pathStyleAccess;

    /**
     * Configura o cliente S3 para comunicação com LocalStack ou AWS
     *
     * @return Cliente S3 configurado
     */
    @Bean
    public S3Client s3Client() {
        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var s3ConfigBuilder = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess);

        var clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3ConfigBuilder.build());

        // Se não for produção, usa o endpoint do LocalStack
        if (s3Endpoint != null && !s3Endpoint.isEmpty() &&
                !s3Endpoint.contains("amazonaws.com")) {
            clientBuilder.endpointOverride(URI.create(s3Endpoint));
        }

        return clientBuilder.build();
    }
}
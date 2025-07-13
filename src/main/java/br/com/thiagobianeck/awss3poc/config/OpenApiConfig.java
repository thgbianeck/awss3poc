package br.com.thiagobianeck.awss3poc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração da documentação OpenAPI 3.0
 *
 * @author Bianeck
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        var contact = new Contact()
                .name("Bianeck")
                .email("bianeck@example.com")
                .url("https://github.com/bianeck");

        var license = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        var info = new Info()
                .title("AWS S3 POC API")
                .version("1.0.0")
                .description("""
                    **API completa para gerenciamento de arquivos com AWS S3**
                    
                    Esta POC demonstra:
                    - ✅ Upload de arquivos únicos e múltiplos
                    - ✅ Download de arquivos com streaming
                    - ✅ Listagem de arquivos com metadados
                    - ✅ Exclusão de arquivos
                    - ✅ Geração de URLs pré-assinadas
                    - ✅ Integração com LocalStack para desenvolvimento
                    - ✅ Testes abrangentes com Testcontainers
                    
                    **Tecnologias utilizadas:**
                    - Spring Boot 3.2+
                    - Java 21
                    - AWS SDK v2
                    - LocalStack
                    - Docker Compose
                    """)
                .contact(contact)
                .license(license);

        var localServer = new Server()
                .url("http://localhost:8080" + contextPath)
                .description("Servidor Local de Desenvolvimento");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}
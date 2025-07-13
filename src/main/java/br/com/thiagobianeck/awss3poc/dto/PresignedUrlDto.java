package br.com.thiagobianeck.awss3poc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * DTO para URLs pré-assinadas do S3
 *
 * @author Bianeck
 */
@Schema(description = "URL pré-assinada para acesso temporário a arquivos")
public record PresignedUrlDto(

        @Schema(description = "Nome do arquivo", example = "documento.pdf")
        String fileName,

        @Schema(description = "Chave do arquivo no S3", example = "files/2024/01/documento-uuid.pdf")
        String key,

        @Schema(description = "URL pré-assinada para acesso direto",
                example = "http://localhost:4566/aws-s3-poc-bucket/files/documento.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...")
        String presignedUrl,

        @Schema(description = "Data de expiração da URL")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant expiresAt,

        @Schema(description = "Duração da validade em minutos", example = "60")
        Integer validityMinutes,

        @Schema(description = "Tipo de operação (GET, PUT, DELETE)", example = "GET")
        String operation
) {

    /**
     * Cria um DTO para URL de download
     */
    public static PresignedUrlDto forDownload(String fileName, String key,
                                              String url, Instant expiresAt,
                                              int validityMinutes) {
        return new PresignedUrlDto(fileName, key, url, expiresAt, validityMinutes, "GET");
    }

    /**
     * Cria um DTO para URL de upload
     */
    public static PresignedUrlDto forUpload(String fileName, String key,
                                            String url, Instant expiresAt,
                                            int validityMinutes) {
        return new PresignedUrlDto(fileName, key, url, expiresAt, validityMinutes, "PUT");
    }

    /**
     * Verifica se a URL ainda é válida
     */
    @Schema(description = "Indica se a URL ainda está válida")
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Calcula quantos minutos restam até a expiração
     */
    @Schema(description = "Minutos restantes até a expiração")
    public long getMinutesUntilExpiry() {
        if (!isValid()) return 0;

        long secondsUntilExpiry = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return secondsUntilExpiry / 60;
    }
}
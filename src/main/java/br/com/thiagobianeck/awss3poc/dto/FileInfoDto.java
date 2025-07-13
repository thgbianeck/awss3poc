package br.com.thiagobianeck.awss3poc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * DTO contendo informações de um arquivo no S3
 *
 * @author Bianeck
 */
@Schema(description = "Informações detalhadas de um arquivo armazenado no S3")
public record FileInfoDto(

        @Schema(description = "Nome do arquivo", example = "documento.pdf")
        String fileName,

        @Schema(description = "Chave única do arquivo no S3", example = "files/2024/01/documento-uuid.pdf")
        String key,

        @Schema(description = "Tamanho do arquivo em bytes", example = "1048576")
        Long size,

        @Schema(description = "Tipo MIME do arquivo", example = "application/pdf")
        String contentType,

        @Schema(description = "ETag do arquivo (hash MD5)", example = "d41d8cd98f00b204e9800998ecf8427e")
        String eTag,

        @Schema(description = "Data da última modificação")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant lastModified,

        @Schema(description = "URL para acesso direto ao arquivo", example = "http://localhost:4566/aws-s3-poc-bucket/files/documento.pdf")
        String url
) {

    /**
     * Cria um FileInfoDto com URL pré-construída
     */
    public static FileInfoDto of(String fileName, String key, Long size,
                                 String contentType, String eTag,
                                 Instant lastModified, String baseUrl) {
        String url = baseUrl + "/" + key;
        return new FileInfoDto(fileName, key, size, contentType, eTag, lastModified, url);
    }

    /**
     * Converte o tamanho do arquivo para formato legível
     */
    @Schema(description = "Tamanho do arquivo em formato legível", example = "1.0 MB")
    public String getFormattedSize() {
        if (size == null) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double bytes = size.doubleValue();
        int unitIndex = 0;

        while (bytes >= 1024 && unitIndex < units.length - 1) {
            bytes /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", bytes, units[unitIndex]);
    }
}
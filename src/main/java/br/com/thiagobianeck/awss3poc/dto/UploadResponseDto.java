package br.com.thiagobianeck.awss3poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * DTO de resposta para operações de upload
 *
 * @author Bianeck
 */
@Schema(description = "Resposta do upload de arquivo(s)")
public record UploadResponseDto(

        @Schema(description = "Indica se o upload foi realizado com sucesso")
        boolean success,

        @Schema(description = "Mensagem descritiva do resultado")
        String message,

        @Schema(description = "Lista de arquivos enviados com sucesso")
        List<FileInfoDto> files,

        @Schema(description = "Data e hora do upload")
        Instant uploadedAt,

        @Schema(description = "Quantidade total de arquivos processados")
        Integer totalFiles,

        @Schema(description = "Tamanho total dos arquivos em bytes")
        Long totalSize
) {

    /**
     * Cria uma resposta de sucesso para upload único
     */
    public static UploadResponseDto success(FileInfoDto file) {
        return new UploadResponseDto(
                true,
                "Arquivo enviado com sucesso",
                List.of(file),
                Instant.now(),
                1,
                file.size()
        );
    }

    /**
     * Cria uma resposta de sucesso para upload múltiplo
     */
    public static UploadResponseDto success(List<FileInfoDto> files) {
        long totalSize = files.stream().mapToLong(FileInfoDto::size).sum();

        return new UploadResponseDto(
                true,
                String.format("%d arquivo(s) enviado(s) com sucesso", files.size()),
                files,
                Instant.now(),
                files.size(),
                totalSize
        );
    }

    /**
     * Cria uma resposta de erro
     */
    public static UploadResponseDto error(String message) {
        return new UploadResponseDto(
                false,
                message,
                List.of(),
                Instant.now(),
                0,
                0L
        );
    }
}
package br.com.thiagobianeck.awss3poc.controller;

import br.com.thiagobianeck.awss3poc.dto.FileInfoDto;
import br.com.thiagobianeck.awss3poc.dto.PresignedUrlDto;
import br.com.thiagobianeck.awss3poc.dto.UploadResponseDto;
import br.com.thiagobianeck.awss3poc.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Controller REST para operações de gerenciamento de arquivos no S3
 *
 * @author Bianeck
 */
@RestController
@RequestMapping("/files")
@Validated
@Tag(name = "Gerenciamento de Arquivos",
        description = "Operações CRUD para arquivos no AWS S3")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(
            summary = "Upload de arquivo único",
            description = "Realiza o upload de um único arquivo para o S3. Suporta arquivos até 50MB."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Arquivo enviado com sucesso",
                    content = @Content(schema = @Schema(implementation = UploadResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido ou erro na validação"),
            @ApiResponse(responseCode = "413", description = "Arquivo muito grande"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponseDto> uploadFile(
            @Parameter(description = "Arquivo a ser enviado", required = true)
            @RequestParam("file") MultipartFile file) {

        logger.info("Recebida requisição de upload para arquivo: {}",
                file.getOriginalFilename());

        FileInfoDto uploadedFile = fileService.uploadFile(file);
        UploadResponseDto response = UploadResponseDto.success(uploadedFile);

        logger.info("Upload concluído com sucesso: {}", uploadedFile.key());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Upload de múltiplos arquivos",
            description = "Realiza o upload de até 10 arquivos simultaneamente. Tamanho total máximo: 100MB."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Arquivos enviados com sucesso",
                    content = @Content(schema = @Schema(implementation = UploadResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Arquivos inválidos ou erro na validação"),
            @ApiResponse(responseCode = "413", description = "Arquivos muito grandes"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponseDto> uploadMultipleFiles(
            @Parameter(description = "Lista de arquivos a serem enviados (máximo 10)", required = true)
            @RequestParam("files") List<MultipartFile> files) {

        logger.info("Recebida requisição de upload múltiplo para {} arquivos", files.size());

        List<FileInfoDto> uploadedFiles = fileService.uploadMultipleFiles(files);
        UploadResponseDto response = UploadResponseDto.success(uploadedFiles);

        logger.info("Upload múltiplo concluído: {} arquivos enviados", uploadedFiles.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Download de arquivo",
            description = "Realiza o download de um arquivo do S3 usando sua chave única."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arquivo baixado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/download/{key:.+}")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "Chave única do arquivo no S3", required = true)
            @PathVariable String key) {

        logger.info("Recebida requisição de download para arquivo: {}", key);

        Resource resource = fileService.downloadFile(key);
        FileInfoDto fileInfo = fileService.getFileInfo(key);

        // Configura headers para download
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                String.format("attachment; filename='%s'", fileInfo.fileName()));
        headers.add(HttpHeaders.CONTENT_TYPE, fileInfo.contentType());
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileInfo.size()));

        logger.info("Download iniciado para arquivo: {} ({})",
                fileInfo.fileName(), fileInfo.getFormattedSize());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    @Operation(
            summary = "Listar todos os arquivos",
            description = "Retorna uma lista com todos os arquivos armazenados no bucket S3."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de arquivos retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = FileInfoDto.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping
    public ResponseEntity<List<FileInfoDto>> listAllFiles() {
        logger.info("Recebida requisição para listar todos os arquivos");

        List<FileInfoDto> files = fileService.listAllFiles();

        logger.info("Listagem concluída: {} arquivos encontrados", files.size());

        return ResponseEntity.ok(files);
    }

    @Operation(
            summary = "Listar arquivos por prefixo",
            description = "Retorna uma lista filtrada de arquivos baseada em um prefixo específico."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista filtrada retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = FileInfoDto.class))),
            @ApiResponse(responseCode = "400", description = "Prefixo inválido"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/prefix/{prefix:.+}")
    public ResponseEntity<List<FileInfoDto>> listFilesByPrefix(
            @Parameter(description = "Prefixo para filtrar arquivos", required = true)
            @PathVariable @NotBlank String prefix) {

        logger.info("Recebida requisição para listar arquivos com prefixo: {}", prefix);

        List<FileInfoDto> files = fileService.listFilesByPrefix(prefix);

        logger.info("Listagem por prefixo concluída: {} arquivos encontrados para '{}'",
                files.size(), prefix);

        return ResponseEntity.ok(files);
    }

    @Operation(
            summary = "Obter informações de arquivo",
            description = "Retorna informações detalhadas de um arquivo específico."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Informações do arquivo retornadas",
                    content = @Content(schema = @Schema(implementation = FileInfoDto.class))),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/info/{key:.+}")
    public ResponseEntity<FileInfoDto> getFileInfo(
            @Parameter(description = "Chave única do arquivo no S3", required = true)
            @PathVariable String key) {

        logger.info("Recebida requisição para obter informações do arquivo: {}", key);

        FileInfoDto fileInfo = fileService.getFileInfo(key);

        logger.info("Informações obtidas para arquivo: {} ({})",
                fileInfo.fileName(), fileInfo.getFormattedSize());

        return ResponseEntity.ok(fileInfo);
    }

    @Operation(
            summary = "Excluir arquivo",
            description = "Remove um arquivo específico do S3 usando sua chave única."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arquivo excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @DeleteMapping("/{key:.+}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @Parameter(description = "Chave única do arquivo no S3", required = true)
            @PathVariable String key) {

        logger.info("Recebida requisição para excluir arquivo: {}", key);

        boolean deleted = fileService.deleteFile(key);

        Map<String, Object> response = Map.of(
                "success", deleted,
                "message", deleted ? "Arquivo excluído com sucesso" : "Falha ao excluir arquivo",
                "key", key
        );

        logger.info("Exclusão de arquivo {}: {}", key, deleted ? "sucesso" : "falha");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Excluir múltiplos arquivos",
            description = "Remove múltiplos arquivos do S3 usando suas chaves únicas."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação de exclusão concluída"),
            @ApiResponse(responseCode = "400", description = "Lista de chaves inválida"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> deleteMultipleFiles(
            @Parameter(description = "Lista de chaves dos arquivos a serem excluídos", required = true)
            @RequestBody @NotEmpty List<@NotBlank String> keys) {

        logger.info("Recebida requisição para excluir {} arquivos", keys.size());

        int deletedCount = fileService.deleteMultipleFiles(keys);

        Map<String, Object> response = Map.of(
                "totalRequested", keys.size(),
                "deletedCount", deletedCount,
                "failedCount", keys.size() - deletedCount,
                "message", String.format("%d de %d arquivos excluídos com sucesso",
                        deletedCount, keys.size())
        );

        logger.info("Exclusão múltipla concluída: {} sucessos de {} solicitados",
                deletedCount, keys.size());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Gerar URL pré-assinada para download",
            description = "Gera uma URL temporária para download direto de um arquivo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL pré-assinada gerada com sucesso",
                    content = @Content(schema = @Schema(implementation = PresignedUrlDto.class))),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos"),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/presigned-url/download/{key:.+}")
    public ResponseEntity<PresignedUrlDto> generatePresignedUrlForDownload(
            @Parameter(description = "Chave única do arquivo no S3", required = true)
            @PathVariable String key,

            @Parameter(description = "Duração da validade em minutos (padrão: 60, máximo: 1440)")
            @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int durationMinutes) {

        logger.info("Recebida requisição para gerar URL pré-assinada de download: {} ({}min)",
                key, durationMinutes);

        Duration duration = Duration.ofMinutes(durationMinutes);
        PresignedUrlDto presignedUrl = fileService.generatePresignedUrlForDownload(key, duration);

        logger.info("URL pré-assinada gerada para download: {} (expira: {})",
                key, presignedUrl.expiresAt());

        return ResponseEntity.ok(presignedUrl);
    }

    @Operation(
            summary = "Gerar URL pré-assinada para upload",
            description = "Gera uma URL temporária para upload direto de um arquivo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL pré-assinada gerada com sucesso",
                    content = @Content(schema = @Schema(implementation = PresignedUrlDto.class))),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @PostMapping("/presigned-url/upload")
    public ResponseEntity<PresignedUrlDto> generatePresignedUrlForUpload(
            @Parameter(description = "Nome do arquivo", required = true)
            @RequestParam @NotBlank String fileName,

            @Parameter(description = "Tipo de conteúdo (MIME type)", required = true)
            @RequestParam @NotBlank String contentType,

            @Parameter(description = "Duração da validade em minutos (padrão: 60, máximo: 1440)")
            @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int durationMinutes) {

        logger.info("Recebida requisição para gerar URL pré-assinada de upload: {} ({}min)",
                fileName, durationMinutes);

        Duration duration = Duration.ofMinutes(durationMinutes);
        PresignedUrlDto presignedUrl = fileService.generatePresignedUrlForUpload(
                fileName, contentType, duration);

        logger.info("URL pré-assinada gerada para upload: {} (expira: {})",
                fileName, presignedUrl.expiresAt());

        return ResponseEntity.ok(presignedUrl);
    }

    @Operation(
            summary = "Verificar existência de arquivo",
            description = "Verifica se um arquivo existe no S3 sem baixá-lo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificação concluída"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/exists/{key:.+}")
    public ResponseEntity<Map<String, Object>> fileExists(
            @Parameter(description = "Chave única do arquivo no S3", required = true)
            @PathVariable String key) {

        logger.info("Recebida requisição para verificar existência do arquivo: {}", key);

        boolean exists = fileService.fileExists(key);

        Map<String, Object> response = Map.of(
                "key", key,
                "exists", exists,
                "message", exists ? "Arquivo existe" : "Arquivo não encontrado"
        );

        logger.info("Verificação de existência para {}: {}", key, exists);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Copiar arquivo",
            description = "Cria uma cópia de um arquivo existente com uma nova chave."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Arquivo copiado com sucesso",
                    content = @Content(schema = @Schema(implementation = FileInfoDto.class))),
            @ApiResponse(responseCode = "404", description = "Arquivo origem não encontrado"),
            @ApiResponse(responseCode = "409", description = "Arquivo destino já existe"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @PostMapping("/copy")
    public ResponseEntity<FileInfoDto> copyFile(
            @Parameter(description = "Chave do arquivo origem", required = true)
            @RequestParam @NotBlank String sourceKey,

            @Parameter(description = "Chave do arquivo destino", required = true)
            @RequestParam @NotBlank String destinationKey) {

        logger.info("Recebida requisição para copiar arquivo: {} -> {}", sourceKey, destinationKey);

        FileInfoDto copiedFile = fileService.copyFile(sourceKey, destinationKey);

        logger.info("Arquivo copiado com sucesso: {} -> {}", sourceKey, destinationKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(copiedFile);
    }

    @Operation(
            summary = "Estatísticas do bucket",
            description = "Retorna estatísticas gerais sobre os arquivos no bucket."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBucketStats() {
        logger.info("Recebida requisição para obter estatísticas do bucket");

        List<FileInfoDto> allFiles = fileService.listAllFiles();

        long totalFiles = allFiles.size();
        long totalSize = allFiles.stream().mapToLong(FileInfoDto::size).sum();

        // Agrupa por extensão
        Map<String, Long> filesByExtension = allFiles.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        file -> br.com.thiagobianeck.awss3poc.util.FileUtils.getFileExtension(file.fileName()).toLowerCase(),
                        java.util.stream.Collectors.counting()));

        Map<String, Object> stats = Map.of(
                "totalFiles", totalFiles,
                "totalSize", totalSize,
                "totalSizeFormatted", br.com.thiagobianeck.awss3poc.util.FileUtils.formatFileSize(totalSize),
                "filesByExtension", filesByExtension,
                "lastUpdated", java.time.Instant.now()
        );

        logger.info("Estatísticas calculadas: {} arquivos, {} total",
                totalFiles, br.com.thiagobianeck.awss3poc.util.FileUtils.formatFileSize(totalSize));

        return ResponseEntity.ok(stats);
    }
}
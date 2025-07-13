package br.com.thiagobianeck.awss3poc.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.Map;

/**
 * Handler global para tratamento de exceções da API
 *
 * @author Bianeck
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Trata exceções de arquivo não encontrado
     */
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFileNotFoundException(
            FileNotFoundException ex, WebRequest request) {

        logger.warn("Arquivo não encontrado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Arquivo não encontrado",
                        ex.getMessage(),
                        request.getDescription(false)
                ));
    }

    /**
     * Trata exceções de upload de arquivo
     */
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<Map<String, Object>> handleFileUploadException(
            FileUploadException ex, WebRequest request) {

        logger.error("Erro no upload de arquivo: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Erro no upload do arquivo",
                        ex.getMessage(),
                        request.getDescription(false)
                ));
    }

    /**
     * Trata exceções de tamanho máximo de upload excedido
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {

        logger.warn("Tamanho máximo de upload excedido: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(createErrorResponse(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        "Arquivo muito grande",
                        "O arquivo excede o tamanho máximo permitido de 50MB",
                        request.getDescription(false)
                ));
    }

    /**
     * Trata exceções específicas do AWS S3
     */
    @ExceptionHandler(NoSuchKeyException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchKeyException(
            NoSuchKeyException ex, WebRequest request) {

        logger.warn("Chave não encontrada no S3: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Arquivo não encontrado no S3",
                        "O arquivo solicitado não existe no bucket",
                        request.getDescription(false)
                ));
    }

    /**
     * Trata exceções gerais do AWS S3
     */
    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<Map<String, Object>> handleS3Exception(
            S3Exception ex, WebRequest request) {

        logger.error("Erro do AWS S3: {}", ex.getMessage(), ex);

        HttpStatus status = switch (ex.statusCode()) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status)
                .body(createErrorResponse(
                        status.value(),
                        "Erro do serviço S3",
                        "Erro interno do serviço de armazenamento: " + ex.awsErrorDetails().errorMessage(),
                        request.getDescription(false)
                ));
    }

    /**
     * Trata argumentos inválidos
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        logger.warn("Argumento inválido: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Argumento inválido",
                        ex.getMessage(),
                        request.getDescription(false)
                ));
    }

    /**
     * Trata exceções gerais não mapeadas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {

        logger.error("Erro interno não mapeado: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Erro interno do servidor",
                        "Ocorreu um erro inesperado. Tente novamente mais tarde.",
                        request.getDescription(false)
                ));
    }

    /**
     * Cria a estrutura padrão de resposta de erro
     */
    private Map<String, Object> createErrorResponse(int status, String error,
                                                    String message, String path) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", error,
                "message", message,
                "path", path.replace("uri=", "")
        );
    }
}
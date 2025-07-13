package br.com.thiagobianeck.awss3poc.service.impl;

import br.com.thiagobianeck.awss3poc.dto.FileInfoDto;
import br.com.thiagobianeck.awss3poc.dto.PresignedUrlDto;
import br.com.thiagobianeck.awss3poc.exception.FileNotFoundException;
import br.com.thiagobianeck.awss3poc.exception.FileUploadException;
import br.com.thiagobianeck.awss3poc.service.FileService;
import br.com.thiagobianeck.awss3poc.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação do serviço de gerenciamento de arquivos no S3
 *
 * @author Bianeck
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final String s3Endpoint;

    public FileServiceImpl(S3Client s3Client,
                           @Value("${aws.s3.bucket-name}") String bucketName,
                           @Value("${aws.s3.endpoint}") String s3Endpoint) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.s3Endpoint = s3Endpoint;

        // Configura o S3Presigner usando as mesmas configurações do S3Client
        this.s3Presigner = S3Presigner.builder()
                .s3Client(s3Client)
                .build();

        logger.info("FileService inicializado com bucket: {} e endpoint: {}", bucketName, s3Endpoint);
    }

    @Override
    public FileInfoDto uploadFile(MultipartFile file) {
        logger.debug("Iniciando upload do arquivo: {}", file.getOriginalFilename());

        // Valida o arquivo
        FileUtils.validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String key = FileUtils.generateFileKey(originalFilename);
        String contentType = FileUtils.getContentType(originalFilename);

        try {
            // Configura metadados do arquivo
            var metadata = createFileMetadata(originalFilename, contentType);

            // Prepara a requisição de upload
            var putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .metadata(metadata)
                    .build();

            // Realiza o upload
            var response = s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            logger.info("Upload realizado com sucesso - Key: {}, ETag: {}", key, response.eTag());

            // Retorna informações do arquivo
            return FileInfoDto.of(
                    originalFilename,
                    key,
                    file.getSize(),
                    contentType,
                    response.eTag(),
                    Instant.now(),
                    buildFileUrl(key)
            );

        } catch (IOException e) {
            logger.error("Erro ao ler arquivo durante upload: {}", originalFilename, e);
            throw new FileUploadException(originalFilename, "Erro ao processar arquivo", e);
        } catch (S3Exception e) {
            logger.error("Erro do S3 durante upload: {}", originalFilename, e);
            throw new FileUploadException(originalFilename, "Erro no serviço de armazenamento", e);
        }
    }

    @Override
    public List<FileInfoDto> uploadMultipleFiles(List<MultipartFile> files) {
        logger.debug("Iniciando upload de {} arquivos", files.size());

        // Valida todos os arquivos antes de começar o upload
        FileUtils.validateFiles(files);

        List<FileInfoDto> uploadedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                FileInfoDto uploadedFile = uploadFile(file);
                uploadedFiles.add(uploadedFile);
                logger.debug("Arquivo {} enviado com sucesso", file.getOriginalFilename());

            } catch (Exception e) {
                logger.error("Falha no upload do arquivo: {}", file.getOriginalFilename(), e);
                failedFiles.add(file.getOriginalFilename());
            }
        }

        // Se houve falhas, registra no log
        if (!failedFiles.isEmpty()) {
            logger.warn("Falha no upload de {} arquivo(s): {}",
                    failedFiles.size(), String.join(", ", failedFiles));
        }

        logger.info("Upload múltiplo concluído: {} sucessos, {} falhas",
                uploadedFiles.size(), failedFiles.size());

        return uploadedFiles;
    }

    @Override
    public Resource downloadFile(String key) {
        logger.debug("Iniciando download do arquivo com key: {}", key);

        if (!fileExists(key)) {
            throw new FileNotFoundException("Arquivo não encontrado", key);
        }

        try {
            var getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            var response = s3Client.getObjectAsBytes(getObjectRequest);

            logger.info("Download realizado com sucesso - Key: {}, Tamanho: {} bytes",
                    key, response.asByteArray().length);

            return new ByteArrayResource(response.asByteArray());

        } catch (NoSuchKeyException e) {
            logger.warn("Arquivo não encontrado para download: {}", key);
            throw new FileNotFoundException("Arquivo não encontrado", key);
        } catch (S3Exception e) {
            logger.error("Erro do S3 durante download: {}", key, e);
            throw new RuntimeException("Erro ao baixar arquivo do S3", e);
        }
    }

    @Override
    public List<FileInfoDto> listAllFiles() {
        logger.debug("Listando todos os arquivos do bucket: {}", bucketName);

        try {
            var listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            var response = s3Client.listObjectsV2(listRequest);

            List<FileInfoDto> files = response.contents().stream()
                    .map(this::convertToFileInfoDto)
                    .collect(Collectors.toList());

            logger.info("Listagem concluída: {} arquivos encontrados", files.size());
            return files;

        } catch (S3Exception e) {
            logger.error("Erro ao listar arquivos do bucket: {}", bucketName, e);
            throw new RuntimeException("Erro ao listar arquivos", e);
        }
    }

    @Override
    public List<FileInfoDto> listFilesByPrefix(String prefix) {
        logger.debug("Listando arquivos com prefixo: {}", prefix);

        try {
            var listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            var response = s3Client.listObjectsV2(listRequest);

            List<FileInfoDto> files = response.contents().stream()
                    .map(this::convertToFileInfoDto)
                    .collect(Collectors.toList());

            logger.info("Listagem por prefixo concluída: {} arquivos encontrados para '{}'",
                    files.size(), prefix);
            return files;

        } catch (S3Exception e) {
            logger.error("Erro ao listar arquivos por prefixo: {}", prefix, e);
            throw new RuntimeException("Erro ao listar arquivos por prefixo", e);
        }
    }

    @Override
    public FileInfoDto getFileInfo(String key) {
        logger.debug("Obtendo informações do arquivo: {}", key);

        try {
            var headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            var response = s3Client.headObject(headRequest);

            // Extrai o nome original do arquivo dos metadados
            String originalFileName = response.metadata().getOrDefault("original-filename",
                    extractFileNameFromKey(key));

            var fileInfo = FileInfoDto.of(
                    originalFileName,
                    key,
                    response.contentLength(),
                    response.contentType(),
                    response.eTag(),
                    response.lastModified(),
                    buildFileUrl(key)
            );

            logger.debug("Informações obtidas com sucesso para: {}", key);
            return fileInfo;

        } catch (NoSuchKeyException e) {
            logger.warn("Arquivo não encontrado para obter informações: {}", key);
            throw new FileNotFoundException("Arquivo não encontrado", key);
        } catch (S3Exception e) {
            logger.error("Erro ao obter informações do arquivo: {}", key, e);
            throw new RuntimeException("Erro ao obter informações do arquivo", e);
        }
    }

    @Override
    public boolean deleteFile(String key) {
        logger.debug("Iniciando exclusão do arquivo: {}", key);

        if (!fileExists(key)) {
            logger.warn("Tentativa de excluir arquivo inexistente: {}", key);
            throw new FileNotFoundException("Arquivo não encontrado", key);
        }

        try {
            var deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);

            logger.info("Arquivo excluído com sucesso: {}", key);
            return true;

        } catch (S3Exception e) {
            logger.error("Erro ao excluir arquivo: {}", key, e);
            return false;
        }
    }

    @Override
    public int deleteMultipleFiles(List<String> keys) {
        logger.debug("Iniciando exclusão de {} arquivos", keys.size());

        if (keys.isEmpty()) {
            return 0;
        }

        try {
            // Prepara a lista de objetos para exclusão
            List<ObjectIdentifier> objectsToDelete = keys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .collect(Collectors.toList());

            var deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();

            var response = s3Client.deleteObjects(deleteRequest);

            int deletedCount = response.deleted().size();
            int errorCount = response.errors().size();

            // Log dos erros, se houver
            response.errors().forEach(error ->
                    logger.warn("Erro ao excluir arquivo {}: {}", error.key(), error.message()));

            logger.info("Exclusão múltipla concluída: {} sucessos, {} erros", deletedCount, errorCount);
            return deletedCount;

        } catch (S3Exception e) {
            logger.error("Erro durante exclusão múltipla", e);
            return 0;
        }
    }

    @Override
    public PresignedUrlDto generatePresignedUrlForDownload(String key, Duration duration) {
        logger.debug("Gerando URL pré-assinada para download: {}", key);

        if (!fileExists(key)) {
            throw new FileNotFoundException("Arquivo não encontrado", key);
        }

        try {
            var getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedUrl = s3Presigner.presignGetObject(presignRequest);

            String fileName = extractFileNameFromKey(key);
            Instant expiresAt = Instant.now().plus(duration);

            logger.info("URL pré-assinada gerada para download: {} (expira em: {})", key, expiresAt);

            return PresignedUrlDto.forDownload(
                    fileName,
                    key,
                    presignedUrl.url().toString(),
                    expiresAt,
                    (int) duration.toMinutes()
            );

        } catch (S3Exception e) {
            logger.error("Erro ao gerar URL pré-assinada para download: {}", key, e);
            throw new RuntimeException("Erro ao gerar URL pré-assinada", e);
        }
    }

    @Override
    public PresignedUrlDto generatePresignedUrlForUpload(String fileName, String contentType, Duration duration) {
        logger.debug("Gerando URL pré-assinada para upload: {}", fileName);

        String key = FileUtils.generateFileKey(fileName);

        try {
            var putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            var presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            var presignedUrl = s3Presigner.presignPutObject(presignRequest);

            Instant expiresAt = Instant.now().plus(duration);

            logger.info("URL pré-assinada gerada para upload: {} (expira em: {})", fileName, expiresAt);

            return PresignedUrlDto.forUpload(
                    fileName,
                    key,
                    presignedUrl.url().toString(),
                    expiresAt,
                    (int) duration.toMinutes()
            );

        } catch (S3Exception e) {
            logger.error("Erro ao gerar URL pré-assinada para upload: {}", fileName, e);
            throw new RuntimeException("Erro ao gerar URL pré-assinada", e);
        }
    }

    @Override
    public boolean fileExists(String key) {
        try {
            var headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            logger.error("Erro ao verificar existência do arquivo: {}", key, e);
            return false;
        }
    }

    @Override
    public FileInfoDto copyFile(String sourceKey, String destinationKey) {
        logger.debug("Copiando arquivo de {} para {}", sourceKey, destinationKey);

        if (!fileExists(sourceKey)) {
            throw new FileNotFoundException("Arquivo origem não encontrado", sourceKey);
        }

        try {
            var copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationKey)
                    .build();

            var response = s3Client.copyObject(copyRequest);

            logger.info("Arquivo copiado com sucesso: {} -> {}", sourceKey, destinationKey);

            // Retorna informações do arquivo copiado
            return getFileInfo(destinationKey);

        } catch (S3Exception e) {
            logger.error("Erro ao copiar arquivo: {} -> {}", sourceKey, destinationKey, e);
            throw new RuntimeException("Erro ao copiar arquivo", e);
        }
    }

    /**
     * Métodos utilitários privados
     */

    private java.util.Map<String, String> createFileMetadata(String originalFilename, String contentType) {
        return java.util.Map.of(
                "original-filename", originalFilename,
                "content-type", contentType,
                "upload-timestamp", Instant.now().toString(),
                "uploaded-by", "s3-poc-application"
        );
    }

    private FileInfoDto convertToFileInfoDto(S3Object s3Object) {
        String fileName = extractFileNameFromKey(s3Object.key());
        return FileInfoDto.of(
                fileName,
                s3Object.key(),
                s3Object.size(),
                FileUtils.getContentType(fileName),
                s3Object.eTag(),
                s3Object.lastModified(),
                buildFileUrl(s3Object.key())
        );
    }

    private String extractFileNameFromKey(String key) {
        if (key == null || key.isEmpty()) {
            return "unknown";
        }

        // Extrai o nome do arquivo da chave (última parte após a última barra)
        int lastSlashIndex = key.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < key.length() - 1) {
            return key.substring(lastSlashIndex + 1);
        }
        return key;
    }

    private String buildFileUrl(String key) {
        return String.format("%s/%s/%s", s3Endpoint, bucketName, key);
    }
}
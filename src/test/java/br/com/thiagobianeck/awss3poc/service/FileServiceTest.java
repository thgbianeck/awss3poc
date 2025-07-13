package br.com.thiagobianeck.awss3poc.service;

import br.com.thiagobianeck.awss3poc.dto.FileInfoDto;
import br.com.thiagobianeck.awss3poc.dto.PresignedUrlDto;
import br.com.thiagobianeck.awss3poc.exception.FileNotFoundException;
import br.com.thiagobianeck.awss3poc.exception.FileUploadException;
import br.com.thiagobianeck.awss3poc.testcontainers.LocalStackTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes de integração para FileService usando Testcontainers
 *
 * @author Bianeck
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(LocalStackTestConfiguration.class)
@DisplayName("FileService Integration Tests")
class FileServiceTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private MockMultipartFile testFile;
    private MockMultipartFile largeFile;
    private MockMultipartFile invalidFile;

    @BeforeEach
    void setUp() {
        // Garante que o bucket existe
        createBucketIfNotExists();

        // Prepara arquivos de teste
        testFile = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "Conteúdo do arquivo de teste PDF".getBytes()
        );

        largeFile = new MockMultipartFile(
                "file",
                "large-file.pdf",
                "application/pdf",
                new byte[51 * 1024 * 1024] // 51MB - excede o limite
        );

        invalidFile = new MockMultipartFile(
                "file",
                "invalid-file.xyz",
                "application/octet-stream",
                "Conteúdo inválido".getBytes()
        );
    }

    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Test
    @DisplayName("Deve fazer upload de arquivo único com sucesso")
    void shouldUploadSingleFileSuccessfully() {
        // When
        FileInfoDto result = fileService.uploadFile(testFile);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.fileName()).isEqualTo("test-document.pdf");
        assertThat(result.size()).isEqualTo(testFile.getSize());
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.key()).startsWith("files/");
        assertThat(result.eTag()).isNotBlank();
        assertThat(result.lastModified()).isNotNull();
        assertThat(result.url()).contains(bucketName);

        // Verifica se o arquivo realmente existe
        assertThat(fileService.fileExists(result.key())).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar arquivo muito grande")
    void shouldRejectOversizedFile() {
        // When & Then
        assertThatThrownBy(() -> fileService.uploadFile(largeFile))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("muito grande");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo com extensão inválida")
    void shouldRejectInvalidFileExtension() {
        // When & Then
        assertThatThrownBy(() -> fileService.uploadFile(invalidFile))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("não é permitida");
    }

    @Test
    @DisplayName("Deve fazer upload de múltiplos arquivos")
    void shouldUploadMultipleFiles() {
        // Given
        MockMultipartFile file1 = new MockMultipartFile("file1", "doc1.txt", "text/plain", "Conteúdo 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file2", "doc2.txt", "text/plain", "Conteúdo 2".getBytes());
        List<MockMultipartFile> files = List.of(file1, file2);

        // When
        List<FileInfoDto> results = fileService.uploadMultipleFiles(files);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).fileName()).isEqualTo("doc1.txt");
        assertThat(results.get(1).fileName()).isEqualTo("doc2.txt");

        // Verifica se ambos os arquivos existem
        results.forEach(file -> assertThat(fileService.fileExists(file.key())).isTrue());
    }

    @Test
    @DisplayName("Deve fazer download de arquivo existente")
    void shouldDownloadExistingFile() throws IOException {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);

        // When
        Resource resource = fileService.downloadFile(uploadedFile.key());

        // Then
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(testFile.getSize());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar baixar arquivo inexistente")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        // When & Then
        assertThatThrownBy(() -> fileService.downloadFile("arquivos/inexistente.pdf"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("Deve listar todos os arquivos")
    void shouldListAllFiles() {
        // Given
        fileService.uploadFile(testFile);
        MockMultipartFile anotherFile = new MockMultipartFile("file", "outro-doc.txt", "text/plain", "Outro conteúdo".getBytes());
        fileService.uploadFile(anotherFile);

        // When
        List<FileInfoDto> files = fileService.listAllFiles();

        // Then
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);
        assertThat(files).extracting(FileInfoDto::fileName)
                .contains("test-document.pdf", "outro-doc.txt");
    }

    @Test
    @DisplayName("Deve listar arquivos por prefixo")
    void shouldListFilesByPrefix() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);
        String prefix = uploadedFile.key().substring(0, uploadedFile.key().lastIndexOf('/'));

        // When
        List<FileInfoDto> files = fileService.listFilesByPrefix(prefix);

        // Then
        assertThat(files).isNotEmpty();
        assertThat(files).allMatch(file -> file.key().startsWith(prefix));
    }

    @Test
    @DisplayName("Deve obter informações de arquivo específico")
    void shouldGetFileInfo() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);

        // When
        FileInfoDto fileInfo = fileService.getFileInfo(uploadedFile.key());

        // Then
        assertThat(fileInfo).isNotNull();
        assertThat(fileInfo.fileName()).isEqualTo("test-document.pdf");
        assertThat(fileInfo.key()).isEqualTo(uploadedFile.key());
        assertThat(fileInfo.size()).isEqualTo(testFile.getSize());
    }

    @Test
    @DisplayName("Deve excluir arquivo existente")
    void shouldDeleteExistingFile() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);
        assertThat(fileService.fileExists(uploadedFile.key())).isTrue();

        // When
        boolean deleted = fileService.deleteFile(uploadedFile.key());

        // Then
        assertThat(deleted).isTrue();
        assertThat(fileService.fileExists(uploadedFile.key())).isFalse();
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar excluir arquivo inexistente")
    void shouldThrowExceptionWhenDeletingNonExistentFile() {
        // When & Then
        assertThatThrownBy(() -> fileService.deleteFile("arquivos/inexistente.pdf"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Deve excluir múltiplos arquivos")
    void shouldDeleteMultipleFiles() {
        // Given
        MockMultipartFile file1 = new MockMultipartFile("file1", "delete1.txt", "text/plain", "Conteúdo 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file2", "delete2.txt", "text/plain", "Conteúdo 2".getBytes());

        List<FileInfoDto> uploadedFiles = fileService.uploadMultipleFiles(List.of(file1, file2));
        List<String> keys = uploadedFiles.stream().map(FileInfoDto::key).toList();

        // When
        int deletedCount = fileService.deleteMultipleFiles(keys);

        // Then
        assertThat(deletedCount).isEqualTo(2);
        keys.forEach(key -> assertThat(fileService.fileExists(key)).isFalse());
    }

    @Test
    @DisplayName("Deve gerar URL pré-assinada para download")
    void shouldGeneratePresignedUrlForDownload() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);
        Duration duration = Duration.ofMinutes(30);

        // When
        PresignedUrlDto presignedUrl = fileService.generatePresignedUrlForDownload(
                uploadedFile.key(), duration);

        // Then
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl.fileName()).isEqualTo("test-document.pdf");
        assertThat(presignedUrl.key()).isEqualTo(uploadedFile.key());
        assertThat(presignedUrl.presignedUrl()).isNotBlank();
        assertThat(presignedUrl.operation()).isEqualTo("GET");
        assertThat(presignedUrl.validityMinutes()).isEqualTo(30);
        assertThat(presignedUrl.isValid()).isTrue();
    }

    @Test
    @DisplayName("Deve gerar URL pré-assinada para upload")
    void shouldGeneratePresignedUrlForUpload() {
        // Given
        String fileName = "novo-arquivo.pdf";
        String contentType = "application/pdf";
        Duration duration = Duration.ofMinutes(15);

        // When
        PresignedUrlDto presignedUrl = fileService.generatePresignedUrlForUpload(
                fileName, contentType, duration);

        // Then
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl.fileName()).isEqualTo(fileName);
        assertThat(presignedUrl.key()).contains("novo-arquivo");
        assertThat(presignedUrl.presignedUrl()).isNotBlank();
        assertThat(presignedUrl.operation()).isEqualTo("PUT");
        assertThat(presignedUrl.validityMinutes()).isEqualTo(15);
        assertThat(presignedUrl.isValid()).isTrue();
    }

    @Test
    @DisplayName("Deve verificar existência de arquivo")
    void shouldCheckFileExists() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);

        // When & Then
        assertThat(fileService.fileExists(uploadedFile.key())).isTrue();
        assertThat(fileService.fileExists("arquivo/inexistente.pdf")).isFalse();
    }

    @Test
    @DisplayName("Deve copiar arquivo existente")
    void shouldCopyExistingFile() {
        // Given
        FileInfoDto originalFile = fileService.uploadFile(testFile);
        String destinationKey = "files/backup/" + originalFile.fileName();

        // When
        FileInfoDto copiedFile = fileService.copyFile(originalFile.key(), destinationKey);

        // Then
        assertThat(copiedFile).isNotNull();
        assertThat(copiedFile.key()).isEqualTo(destinationKey);
        assertThat(copiedFile.size()).isEqualTo(originalFile.size());

        // Verifica se ambos os arquivos existem
        assertThat(fileService.fileExists(originalFile.key())).isTrue();
        assertThat(fileService.fileExists(destinationKey)).isTrue();
    }

    @Test
    @DisplayName("Deve lançar exceção ao copiar arquivo inexistente")
    void shouldThrowExceptionWhenCopyingNonExistentFile() {
        // When & Then
        assertThatThrownBy(() -> fileService.copyFile("arquivo/inexistente.pdf", "destino/arquivo.pdf"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("não encontrado");
    }

    @Test
    @DisplayName("Deve rejeitar lista vazia de arquivos para upload múltiplo")
    void shouldRejectEmptyFileListForMultipleUpload() {
        // When & Then
        assertThatThrownBy(() -> fileService.uploadMultipleFiles(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode estar vazia");
    }

    @Test
    @DisplayName("Deve rejeitar mais de 10 arquivos para upload múltiplo")
    void shouldRejectTooManyFilesForMultipleUpload() {
        // Given
        List<MockMultipartFile> manyFiles = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> new MockMultipartFile("file" + i, "file" + i + ".txt", "text/plain", "Conteúdo".getBytes()))
                .collect(java.util.stream.Collectors.toList());

        // When & Then
        assertThatThrownBy(() -> fileService.uploadMultipleFiles(manyFiles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Máximo de 10 arquivos");
    }

    @Test
    @DisplayName("Deve formatar tamanho do arquivo corretamente")
    void shouldFormatFileSizeCorrectly() {
        // Given
        FileInfoDto uploadedFile = fileService.uploadFile(testFile);

        // When
        String formattedSize = uploadedFile.getFormattedSize();

        // Then
        assertThat(formattedSize).matches("\\d+\\.\\d+ [KMGT]?B");
    }
}
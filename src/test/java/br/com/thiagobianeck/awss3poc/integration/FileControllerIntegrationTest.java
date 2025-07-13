package br.com.thiagobianeck.awss3poc.integration;

import br.com.thiagobianeck.awss3poc.testcontainers.LocalStackTestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração completos para FileController
 *
 * @author Bianeck
 */
@SpringBootTest
@AutoConfigureWebMvc
@Testcontainers
@ActiveProfiles("test")
@Import(LocalStackTestConfiguration.class)
@DisplayName("FileController Integration Tests")
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private MockMultipartFile testFile;
    private MockMultipartFile imageFile;

    @BeforeEach
    void setUp() {
        // Garante que o bucket existe
        createBucketIfNotExists();

        // Prepara arquivos de teste
        testFile = new MockMultipartFile(
                "file",
                "integration-test.pdf",
                "application/pdf",
                "Conteúdo do arquivo de teste para integração".getBytes()
        );

        imageFile = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "Dados binários da imagem de teste".getBytes()
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
    @DisplayName("POST /files/upload - Deve fazer upload de arquivo único")
    void shouldUploadSingleFile() throws Exception {
        mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Arquivo enviado com sucesso"))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files[0].fileName").value("integration-test.pdf"))
                .andExpect(jsonPath("$.files[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.files[0].key").value(startsWith("files/")))
                .andExpect(jsonPath("$.files[0].size").value(testFile.getSize()))
                .andExpect(jsonPath("$.totalFiles").value(1));
    }

    @Test
    @DisplayName("POST /files/upload-multiple - Deve fazer upload de múltiplos arquivos")
    void shouldUploadMultipleFiles() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "doc1.txt", "text/plain", "Conteúdo 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "doc2.txt", "text/plain", "Conteúdo 2".getBytes());

        mockMvc.perform(multipart("/api/files/upload-multiple")
                        .file(file1)
                        .file(file2))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files", hasSize(2)))
                .andExpect(jsonPath("$.totalFiles").value(2));
    }

    @Test
    @DisplayName("POST /files/upload - Deve rejeitar arquivo muito grande")
    void shouldRejectOversizedFile() throws Exception {
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-file.pdf",
                "application/pdf",
                new byte[51 * 1024 * 1024] // 51MB
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(largeFile))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("muito grande")));
    }

    @Test
    @DisplayName("POST /files/upload - Deve rejeitar extensão inválida")
    void shouldRejectInvalidExtension() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.xyz",
                "application/octet-stream",
                "Conteúdo inválido".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(invalidFile))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("não é permitida")));
    }

    @Test
    @DisplayName("GET /files/download/{key} - Deve fazer download de arquivo existente")
    void shouldDownloadExistingFile() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");

        // Agora faz download
        mockMvc.perform(get("/api/files/download/{key}", fileKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(testFile.getBytes()));
    }

    @Test
    @DisplayName("GET /files/download/{key} - Deve retornar 404 para arquivo inexistente")
    void shouldReturn404ForNonExistentFile() throws Exception {
        mockMvc.perform(get("/api/files/download/{key}", "files/inexistente.pdf"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("não encontrado")));
    }

    @Test
    @DisplayName("GET /files - Deve listar todos os arquivos")
    void shouldListAllFiles() throws Exception {
        // Primeiro faz upload de alguns arquivos
        mockMvc.perform(multipart("/api/files/upload").file(testFile))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/files/upload").file(imageFile))
                .andExpect(status().isCreated());

        // Lista todos os arquivos
        mockMvc.perform(get("/api/files"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].fileName", hasItems("integration-test.pdf", "test-image.jpg")));
    }

    @Test
    @DisplayName("GET /files/prefix/{prefix} - Deve listar arquivos por prefixo")
    void shouldListFilesByPrefix() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");
        String prefix = fileKey.substring(0, fileKey.lastIndexOf('/'));

        // Lista por prefixo
        mockMvc.perform(get("/api/files/prefix/{prefix}", prefix))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].key", everyItem(startsWith(prefix))));
    }

    @Test
    @DisplayName("GET /files/info/{key} - Deve obter informações de arquivo")
    void shouldGetFileInfo() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");

        // Obtém informações
        mockMvc.perform(get("/api/files/info/{key}", fileKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("integration-test.pdf"))
                .andExpect(jsonPath("$.key").value(fileKey))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.size").value(testFile.getSize()));
    }

    @Test
    @DisplayName("DELETE /files/{key} - Deve excluir arquivo existente")
    void shouldDeleteExistingFile() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");

        // Exclui o arquivo
        mockMvc.perform(delete("/api/files/{key}", fileKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Arquivo excluído com sucesso"))
                .andExpect(jsonPath("$.key").value(fileKey));

        // Verifica se foi realmente excluído
        mockMvc.perform(get("/api/files/info/{key}", fileKey))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /files/batch - Deve excluir múltiplos arquivos")
    void shouldDeleteMultipleFiles() throws Exception {
        // Primeiro faz upload de múltiplos arquivos
        MockMultipartFile file1 = new MockMultipartFile("files", "delete1.txt", "text/plain", "Conteúdo 1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "delete2.txt", "text/plain", "Conteúdo 2".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload-multiple")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        List<String> keys = files.stream().map(file -> (String) file.get("key")).toList();

        // Exclui múltiplos arquivos
        mockMvc.perform(delete("/api/files/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keys)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.deletedCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    @DisplayName("GET /files/presigned-url/download/{key} - Deve gerar URL pré-assinada para download")
    void shouldGeneratePresignedUrlForDownload() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");

        // Gera URL pré-assinada
        mockMvc.perform(get("/api/files/presigned-url/download/{key}", fileKey)
                        .param("durationMinutes", "30"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("integration-test.pdf"))
                .andExpect(jsonPath("$.key").value(fileKey))
                .andExpect(jsonPath("$.presignedUrl").value(not(emptyString())))
                .andExpect(jsonPath("$.operation").value("GET"))
                .andExpect(jsonPath("$.validityMinutes").value(30));
    }

    @Test
    @DisplayName("POST /files/presigned-url/upload - Deve gerar URL pré-assinada para upload")
    void shouldGeneratePresignedUrlForUpload() throws Exception {
        mockMvc.perform(post("/api/files/presigned-url/upload")
                        .param("fileName", "novo-arquivo.pdf")
                        .param("contentType", "application/pdf")
                        .param("durationMinutes", "60"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("novo-arquivo.pdf"))
                .andExpect(jsonPath("$.key").value(containsString("novo-arquivo")))
                .andExpect(jsonPath("$.presignedUrl").value(not(emptyString())))
                .andExpect(jsonPath("$.operation").value("PUT"))
                .andExpect(jsonPath("$.validityMinutes").value(60));
    }

    @Test
    @DisplayName("GET /files/exists/{key} - Deve verificar existência de arquivo")
    void shouldCheckFileExists() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String fileKey = (String) files.get(0).get("key");

        // Verifica existência do arquivo que existe
        mockMvc.perform(get("/api/files/exists/{key}", fileKey))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(fileKey))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.message").value("Arquivo existe"));

        // Verifica arquivo inexistente
        mockMvc.perform(get("/api/files/exists/{key}", "arquivo/inexistente.pdf"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.message").value("Arquivo não encontrado"));
    }

    @Test
    @DisplayName("POST /files/copy - Deve copiar arquivo existente")
    void shouldCopyExistingFile() throws Exception {
        // Primeiro faz upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(testFile))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = uploadResult.getResponse().getContentAsString();
        Map<String, Object> uploadResponse = objectMapper.readValue(responseContent, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadResponse.get("files");
        String sourceKey = (String) files.get(0).get("key");
        String destinationKey = "files/backup/" + testFile.getOriginalFilename();

        // Copia o arquivo
        mockMvc.perform(post("/api/files/copy")
                        .param("sourceKey", sourceKey)
                        .param("destinationKey", destinationKey))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(destinationKey))
                .andExpect(jsonPath("$.fileName").value(testFile.getOriginalFilename()));

        // Verifica se ambos os arquivos existem
        mockMvc.perform(get("/api/files/exists/{key}", sourceKey))
                .andExpect(jsonPath("$.exists").value(true));

        mockMvc.perform(get("/api/files/exists/{key}", destinationKey))
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    @DisplayName("GET /files/stats - Deve retornar estatísticas do bucket")
    void shouldReturnBucketStats() throws Exception {
        // Primeiro faz upload de alguns arquivos
        mockMvc.perform(multipart("/api/files/upload").file(testFile))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/files/upload").file(imageFile))
                .andExpect(status().isCreated());

        // Obtém estatísticas
        mockMvc.perform(get("/api/files/stats"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalSize").value(greaterThan(0)))
                .andExpect(jsonPath("$.totalSizeFormatted").value(not(emptyString())))
                .andExpect(jsonPath("$.filesByExtension").isMap())
                .andExpect(jsonPath("$.lastUpdated").value(not(emptyString())));
    }

    @Test
    @DisplayName("Deve validar parâmetros de duração para URLs pré-assinadas")
    void shouldValidateDurationParameters() throws Exception {
        // Testa duração muito baixa
        mockMvc.perform(post("/api/files/presigned-url/upload")
                        .param("fileName", "test.pdf")
                        .param("contentType", "application/pdf")
                        .param("durationMinutes", "0"))
                .andDo(print())
                .andExpect(status().isBadRequest());

        // Testa duração muito alta
        mockMvc.perform(post("/api/files/presigned-url/upload")
                        .param("fileName", "test.pdf")
                        .param("contentType", "application/pdf")
                        .param("durationMinutes", "1500"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
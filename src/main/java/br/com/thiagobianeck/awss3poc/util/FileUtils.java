package br.com.thiagobianeck.awss3poc.util;

import br.com.thiagobianeck.awss3poc.exception.FileUploadException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utilitários para manipulação e validação de arquivos
 *
 * @author Bianeck
 */
public final class FileUtils {

    private static final Pattern VALID_FILENAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]+$");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",  // Imagens
            "pdf", "doc", "docx", "txt", "rtf",          // Documentos
            "xls", "xlsx", "csv",                        // Planilhas
            "zip", "rar", "7z",                          // Compactados
            "mp3", "wav", "ogg",                         // Áudio
            "mp4", "avi", "mkv", "webm"                  // Vídeo
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private FileUtils() {
        // Classe utilitária - construtor privado
    }

    /**
     * Valida se o arquivo atende aos critérios de upload
     */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("N/A", "Arquivo não pode estar vazio");
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new FileUploadException("N/A", "Nome do arquivo não pode estar vazio");
        }

        // Valida nome do arquivo
        if (!VALID_FILENAME_PATTERN.matcher(originalFilename).matches()) {
            throw new FileUploadException(originalFilename,
                    "Nome do arquivo contém caracteres inválidos");
        }

        // Valida extensão
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileUploadException(originalFilename,
                    String.format("Extensão '%s' não é permitida. Extensões permitidas: %s",
                            extension, String.join(", ", ALLOWED_EXTENSIONS)));
        }

        // Valida tamanho
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileUploadException(originalFilename,
                    String.format("Arquivo muito grande (%.2f MB). Tamanho máximo: 50 MB",
                            file.getSize() / (1024.0 * 1024.0)));
        }
    }

    /**
     * Valida múltiplos arquivos
     */
    public static void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Lista de arquivos não pode estar vazia");
        }

        if (files.size() > 10) {
            throw new IllegalArgumentException("Máximo de 10 arquivos por upload");
        }

        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 100 * 1024 * 1024) { // 100MB total
            throw new IllegalArgumentException("Tamanho total dos arquivos excede 100MB");
        }

        // Valida cada arquivo individualmente
        files.forEach(FileUtils::validateFile);
    }

    /**
     * Gera uma chave única para o arquivo no S3
     */
    public static String generateFileKey(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            throw new IllegalArgumentException("Nome do arquivo não pode estar vazio");
        }

        String extension = getFileExtension(originalFilename);
        String nameWithoutExtension = getFileNameWithoutExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        // Formato: files/ano/mes/nome-uuid.extensao
        java.time.LocalDate now = java.time.LocalDate.now();
        String yearMonth = String.format("%d/%02d", now.getYear(), now.getMonthValue());

        return String.format("files/%s/%s-%s.%s",
                yearMonth, nameWithoutExtension, uuid, extension);
    }

    /**
     * Extrai a extensão do arquivo
     */
    public static String getFileExtension(String filename) {
        if (StringUtils.isBlank(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Extrai o nome do arquivo sem a extensão
     */
    public static String getFileNameWithoutExtension(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return filename;
        }

        return filename.substring(0, lastDotIndex);
    }

    /**
     * Determina o Content-Type baseado na extensão do arquivo
     */
    public static String getContentType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        return switch (extension) {
            // Imagens
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";

            // Documentos
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            case "rtf" -> "application/rtf";

            // Planilhas
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";

            // Compactados
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";

            // Áudio
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";

            // Vídeo
            case "mp4" -> "video/mp4";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "webm" -> "video/webm";

            default -> "application/octet-stream";
        };
    }

    /**
     * Sanitiza nome de arquivo removendo caracteres especiais
     */
    public static String sanitizeFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "file";
        }

        // Remove ou substitui caracteres problemáticos
        return filename
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .trim();
    }

    /**
     * Verifica se a extensão do arquivo é de imagem
     */
    public static boolean isImageFile(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension);
    }

    /**
     * Verifica se a extensão do arquivo é de documento
     */
    public static boolean isDocumentFile(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return Set.of("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "csv").contains(extension);
    }

    /**
     * Formata tamanho de arquivo para leitura humana
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = bytes;
        int unitIndex = 0;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }
}
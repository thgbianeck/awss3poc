package br.com.thiagobianeck.awss3poc.exception;

/**
 * Exceção lançada durante problemas no upload de arquivos
 *
 * @author Bianeck
 */
public class FileUploadException extends RuntimeException {

    private final String fileName;
    private final String reason;

    public FileUploadException(String fileName, String reason) {
        super(String.format("Erro no upload do arquivo '%s': %s", fileName, reason));
        this.fileName = fileName;
        this.reason = reason;
    }

    public FileUploadException(String fileName, String reason, Throwable cause) {
        super(String.format("Erro no upload do arquivo '%s': %s", fileName, reason), cause);
        this.fileName = fileName;
        this.reason = reason;
    }

    public String getFileName() {
        return fileName;
    }

    public String getReason() {
        return reason;
    }
}
package br.com.thiagobianeck.awss3poc.exception;

/**
 * Exceção lançada quando um arquivo não é encontrado no S3
 *
 * @author Bianeck
 */
public class FileNotFoundException extends RuntimeException {

    private final String fileName;
    private final String key;

    public FileNotFoundException(String fileName) {
        super(String.format("Arquivo '%s' não encontrado", fileName));
        this.fileName = fileName;
        this.key = null;
    }

    public FileNotFoundException(String fileName, String key) {
        super(String.format("Arquivo '%s' com chave '%s' não encontrado", fileName, key));
        this.fileName = fileName;
        this.key = key;
    }

    public FileNotFoundException(String fileName, Throwable cause) {
        super(String.format("Arquivo '%s' não encontrado", fileName), cause);
        this.fileName = fileName;
        this.key = null;
    }

    public String getFileName() {
        return fileName;
    }

    public String getKey() {
        return key;
    }
}
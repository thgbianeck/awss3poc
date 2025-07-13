package br.com.thiagobianeck.awss3poc.service;

import br.com.thiagobianeck.awss3poc.dto.FileInfoDto;
import br.com.thiagobianeck.awss3poc.dto.PresignedUrlDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * Interface do serviço de gerenciamento de arquivos no S3
 *
 * @author Bianeck
 */
public interface FileService {

    /**
     * Faz upload de um único arquivo para o S3
     *
     * @param file Arquivo a ser enviado
     * @return Informações do arquivo enviado
     */
    FileInfoDto uploadFile(MultipartFile file);

    /**
     * Faz upload de múltiplos arquivos para o S3
     *
     * @param files Lista de arquivos a serem enviados
     * @return Lista com informações dos arquivos enviados
     */
    List<FileInfoDto> uploadMultipleFiles(List<MultipartFile> files);

    /**
     * Faz download de um arquivo do S3
     *
     * @param key Chave do arquivo no S3
     * @return Resource contendo o arquivo
     */
    Resource downloadFile(String key);

    /**
     * Lista todos os arquivos do bucket
     *
     * @return Lista com informações de todos os arquivos
     */
    List<FileInfoDto> listAllFiles();

    /**
     * Lista arquivos com filtro por prefixo
     *
     * @param prefix Prefixo para filtrar arquivos
     * @return Lista filtrada de arquivos
     */
    List<FileInfoDto> listFilesByPrefix(String prefix);

    /**
     * Obtém informações de um arquivo específico
     *
     * @param key Chave do arquivo no S3
     * @return Informações do arquivo
     */
    FileInfoDto getFileInfo(String key);

    /**
     * Exclui um arquivo do S3
     *
     * @param key Chave do arquivo a ser excluído
     * @return true se excluído com sucesso
     */
    boolean deleteFile(String key);

    /**
     * Exclui múltiplos arquivos do S3
     *
     * @param keys Lista de chaves dos arquivos a serem excluídos
     * @return Quantidade de arquivos excluídos com sucesso
     */
    int deleteMultipleFiles(List<String> keys);

    /**
     * Gera URL pré-assinada para download
     *
     * @param key Chave do arquivo
     * @param duration Duração da validade da URL
     * @return DTO com informações da URL pré-assinada
     */
    PresignedUrlDto generatePresignedUrlForDownload(String key, Duration duration);

    /**
     * Gera URL pré-assinada para upload
     *
     * @param fileName Nome do arquivo
     * @param contentType Tipo de conteúdo
     * @param duration Duração da validade da URL
     * @return DTO com informações da URL pré-assinada
     */
    PresignedUrlDto generatePresignedUrlForUpload(String fileName, String contentType, Duration duration);

    /**
     * Verifica se um arquivo existe no S3
     *
     * @param key Chave do arquivo
     * @return true se o arquivo existe
     */
    boolean fileExists(String key);

    /**
     * Copia um arquivo para um novo local no S3
     *
     * @param sourceKey Chave do arquivo origem
     * @param destinationKey Chave do arquivo destino
     * @return Informações do arquivo copiado
     */
    FileInfoDto copyFile(String sourceKey, String destinationKey);
}
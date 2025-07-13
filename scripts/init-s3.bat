@echo off
setlocal enabledelayedexpansion

REM ================================================
REM Script de Inicialização S3 LocalStack
REM ================================================

echo.
echo [INFO] Inicializando bucket S3 no LocalStack...
echo.

REM Verifica se awslocal está disponível
where awslocal >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERRO] awslocal não encontrado! Instale o LocalStack CLI primeiro.
    echo [DICA] Execute: pip install localstack[cli]
    pause
    exit /b 1
)

REM Aguarda o LocalStack estar totalmente carregado
echo [INFO] Aguardando LocalStack carregar completamente...
timeout /t 5 /nobreak >nul

REM Cria o bucket principal da aplicação
echo [INFO] Criando bucket 'aws-s3-poc-bucket'...
awslocal s3 mb s3://aws-s3-poc-bucket --region us-east-1

REM Verifica se o comando foi executado com sucesso
if %errorlevel% neq 0 (
    echo [ERRO] Falha ao executar comando de criação do bucket
    pause
    exit /b 1
)

REM Verifica se o bucket foi realmente criado
echo [INFO] Verificando se bucket foi criado...
awslocal s3 ls | findstr "aws-s3-poc-bucket" >nul
if %errorlevel% equ 0 (
    echo [SUCESSO] ✅ Bucket 'aws-s3-poc-bucket' criado com sucesso!
) else (
    echo [ERRO] ❌ Bucket não foi encontrado após criação
    pause
    exit /b 1
)

REM Lista todos os buckets para confirmação
echo.
echo [INFO] 📦 Buckets disponíveis no LocalStack:
echo ================================================
awslocal s3 ls
echo ================================================

echo.
echo [SUCESSO] 🚀 LocalStack S3 inicializado e pronto para uso!
echo.

pause
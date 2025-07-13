@echo off
setlocal enabledelayedexpansion

REM Script de inicialização do bucket S3 no LocalStack
echo Inicializando bucket S3 no LocalStack...

REM Aguarda o LocalStack estar totalmente carregado
echo Aguardando LocalStack carregar...
timeout /t 5 /nobreak >nul

REM Cria o bucket principal da aplicação
echo Criando bucket aws-s3-poc-bucket...
aws --endpoint-url=http://localhost:4566 s3 mb s3://aws-s3-poc-bucket --region us-east-1

REM Verifica se o bucket foi criado com sucesso
aws --endpoint-url=http://localhost:4566 s3 ls | findstr "aws-s3-poc-bucket" >nul
if %errorlevel% equ 0 (
    echo ✅ Bucket 'aws-s3-poc-bucket' criado com sucesso!
) else (
    echo ❌ Erro ao criar bucket 'aws-s3-poc-bucket'
    exit /b 1
)

REM Lista todos os buckets para confirmação
echo 📦 Buckets disponíveis:
aws --endpoint-url=http://localhost:4566 s3 ls

echo 🚀 LocalStack S3 inicializado e pronto para uso!

pause
#!/bin/bash

# Script de inicialização do bucket S3 no LocalStack
echo "Inicializando bucket S3 no LocalStack..."

# Aguarda o LocalStack estar totalmente carregado
sleep 5

# Cria o bucket principal da aplicação
awslocal s3 mb s3://aws-s3-poc-bucket --region us-east-1

# Verifica se o bucket foi criado com sucesso
if awslocal s3 ls | grep -q "aws-s3-poc-bucket"; then
    echo "✅ Bucket 'aws-s3-poc-bucket' criado com sucesso!"
else
    echo "❌ Erro ao criar bucket 'aws-s3-poc-bucket'"
    exit 1
fi

# Lista todos os buckets para confirmação
echo "📦 Buckets disponíveis:"
awslocal s3 ls

echo "🚀 LocalStack S3 inicializado e pronto para uso!"
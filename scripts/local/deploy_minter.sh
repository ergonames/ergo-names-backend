#!/bin/bash

$NETWORK=${network:-testnet}

# https://www.brianchildress.co/named-parameters-in-bash/
while [ $# -gt 0 ]; do
  if [[ $1 == *"--"* ]]; then
    param="${1/--/}"
    declare $param="$2"
    # echo $1 $2 // Optional to see the parameter:value result
  fi
  shift
done

AWS_ACCOUNT_NUMBER=<ACCOUNT_NUMBER>
AWS_REGION=us-east-2
ECR_REPO_NAME="$AWS_ACCOUNT_NUMBER.dkr.ecr.$AWS_REGION.amazonaws.com/minter"
IMAGE_TAG=latest
IMAGE_URI=$ECR_REPO_NAME:$IMAGE_TAG
FUNCTION_NAME=minter-$NETWORK

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_NUMBER.dkr.ecr.$AWS_REGION.amazonaws.com

# Build minter image
docker build -t $REPOSITORY:$TAG ../

# Push image to ECR
docker push $IMAGE_URI

# Update lambda code
aws lambda update-function-code --function-name $FUNCTION_NAME --image-uri $IMAGE_URI

aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin ACCOUNT_NUMBER.dkr.ecr.us-east-2.amazonaws.com

aws ecr-public get-login-password --region us-west-2 | docker login --username AWS --password-stdin public.ecr.aws
TAG="ACCOUNT_NUMBER.dkr.ecr.us-west-2.amazonaws.com/minter:latest"
docker build -t $TAG .
docker push $TAG
aws lambda update-function-code --function-name minter-testnet --image-uri $TAG

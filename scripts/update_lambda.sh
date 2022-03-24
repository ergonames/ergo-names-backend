aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 343255070305.dkr.ecr.us-west-2.amazonaws.com

aws ecr-public get-login-password --region us-west-2 | docker login --username AWS --password-stdin public.ecr.aws
TAG="ACCOUNTNUMBER.dkr.ecr.us-west-2.amazonaws.com/minter:latest"
docker build -t $TAG .
docker push $TAG
aws lambda update-function-code --function-name minter-dev --image-uri $TAG

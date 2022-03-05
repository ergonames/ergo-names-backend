aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 343255070305.dkr.ecr.us-west-2.amazonaws.com

aws ecr-public get-login-password --region us-west-2 | docker login --username AWS --password-stdin public.ecr.aws
docker build -t 343255070305.dkr.ecr.us-west-2.amazonaws.com/minter:latest .
docker push 343255070305.dkr.ecr.us-west-2.amazonaws.com/minter:latest
aws lambda update-function-code --function-name minter-dev --image-uri 343255070305.dkr.ecr.us-west-2.amazonaws.com/minter:latest

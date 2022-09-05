param ( $Network = "testnet" )
$awsAccountNumber=<ACCOUNT_NUMBER>
$awsRegion="us-east-2"
$ecrRepoName="$awsAccountNumber.dkr.ecr.$awsRegion.amazonaws.com/minter"
$imageTag="latest"
$imageUri="${ecrRepoName}:${imageTag}"
$functionName="minter-$Network"

# Login to ECR
docker login --username AWS -p $(aws ecr get-login-password --region $awsRegion) "$awsAccountNumber.dkr.ecr.us-east-2.amazonaws.com"

# Build minter image
docker build -t "${ecrRepoName}:${imageTag}" ../

# Push image to ECR
docker push $imageUri

# Update lambda code
aws lambda update-function-code --function-name $functionName --image-uri $imageUri
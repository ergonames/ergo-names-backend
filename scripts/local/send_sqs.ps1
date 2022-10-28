$queueUrl=$args[0]
$paymentTxId=$args[1]
$mintingRequestBoxId=$args[2]

aws sqs send-message --queue-url $queueUrl --message-body '{ \"paymentTxId\": \"$paymentTxId\", \"mintRequestBoxId\": \"$mintingRequestBoxId\"}'
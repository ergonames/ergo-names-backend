FROM mozilla/sbt as builder
COPY . /lambda/src/
WORKDIR /lambda/src/
RUN sbt assembly

FROM public.ecr.aws/lambda/java:11
COPY --from=builder /lambda/src/target/scala-2.12/ergo-names-backend-assembly-0.1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["scenarios.ProcessMintingRequest::lambdaEventHandler"]

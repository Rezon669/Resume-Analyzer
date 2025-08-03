package com.app.resumeanalyzer.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

import com.app.resumeanalyzer.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;

@Slf4j
public class LambdaHandler implements RequestHandler<S3Event, String> {

    private ResumeService resumeService;

    public LambdaHandler() {

        S3Client s3Client = S3Client.create();
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        SecretsManagerClient secretsManagerClient = SecretsManagerClient.create();
        RestTemplate restTemplate = new RestTemplate();

        this.resumeService = new ResumeService(restTemplate, s3Client, dynamoDbClient, secretsManagerClient);
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        log.info("Lambda Handler method execution started");

        s3Event.getRecords().forEach(record -> {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();

            try {
                log.info("Calling resume handler method");
                resumeService.handleResumeUpload(bucket, key);
            } catch (IOException | TikaException e) {
                throw new RuntimeException("Error processing resume", e);
            }
        });

        return "Resume processed successfully";
    }
}

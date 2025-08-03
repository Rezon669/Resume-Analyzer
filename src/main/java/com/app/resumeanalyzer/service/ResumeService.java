package com.app.resumeanalyzer.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.app.resumeanalyzer.model.Candidate;
import com.app.resumeanalyzer.model.Experience;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

@Service
@Slf4j
public class ResumeService {

	String secretName = System.getenv("SECRET_NAME");
	String prompt = System.getenv("PROMPT");
	String apiKey;

	private final RestTemplate restTemplate;
    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final SecretsManagerClient secretsManagerClient;

	public ResumeService(RestTemplate restTemplate, S3Client s3Client, DynamoDbClient dynamoDbClient, SecretsManagerClient secretsManagerClient) {
		this.restTemplate = restTemplate;
		this.s3Client = s3Client;
		this.dynamoDbClient = dynamoDbClient;
		this.secretsManagerClient = secretsManagerClient;
	}

	public void handleResumeUpload(String bucket, String key) throws IOException, TikaException {

        log.info("Inside Handler method");

		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

		ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(request);

        log.info("Successfully fetched the Object from S3 {} ", s3Object);

		String resumeContent = extractTextFromFile(s3Object);

        log.info("Extracted the text content from resume");

		sendToOpenAI(resumeContent);

	}

	private String fetchApiKey() {
		try {
			log.info("Fetching API key from the Secrets manager");

			GetSecretValueRequest secretReq = GetSecretValueRequest.builder()
					.secretId(secretName)
					.build();

			GetSecretValueResponse secretResponse = secretsManagerClient.getSecretValue(secretReq);
			String secretString = secretResponse.secretString();

			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(secretString);

            log.info("Successfully fetched the API Key");

			 return node.get("openai_api_key").asText();

		} catch (Exception e) {
			log.error("Error fetching secret from Secrets Manager", e);
			throw new RuntimeException("Unable to fetch secret", e);
		}
	}


	String extractTextFromFile(ResponseInputStream<GetObjectResponse> s3Object) throws IOException, TikaException, TikaException {
		Tika tika = new Tika();
        log.info("Extracting the text content from Resume");
		return tika.parseToString(s3Object);

	}


	private void sendToOpenAI(String resumeText) throws JsonProcessingException {

		String url = "https://api.openai.com/v1/chat/completions";

        log.info("Building the response body to call OpenAI API");

		Map<String, Object> requestBody = new HashMap<>();

		requestBody.put("model", "gpt-3.5-turbo");

		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", prompt));
		messages.add(Map.of("role", "user", "content", resumeText));
		requestBody.put("messages", messages);

		requestBody.put("temperature", 0.2);

        log.info("Calling secrets manager method");

        apiKey = fetchApiKey();

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("Calling Open AI API");

		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

		ObjectMapper mapper = new ObjectMapper();

        log.info("Parsing the JSON response");

        JsonNode root = mapper.readTree(response.getBody());

        String contentString = root.get("choices").get(0).get("message").get("content").asText();

        JsonNode content = mapper.readTree(contentString);

        log.info("Extracting the Candidate information from JSON response");

        String candidateId = UUID.randomUUID().toString();

        Candidate candidate = Candidate.builder()
                .candidateId(candidateId)
                .name(content.get("name").asText())
                .email(content.get("email").asText())
                .phone(content.get("phone").asText())
                .totalExp(content.get("totalExp").asText())
                .skills(mapper.convertValue(content.get("skills"), new TypeReference<List<String>>() {}))
                .location(content.get("location").asText())
                .build();

        log.info("Extracting the Experience information from JSON response");

        List<Experience> experiences = new ArrayList<>();
        for (JsonNode expNode : content.get("experience")) {
            Experience experience = Experience.builder()
                    .experienceId(UUID.randomUUID().toString())
                    .candidateId(candidateId)
                    .company(expNode.get("company").asText())
                    .position(expNode.get("role").asText())
                    .duration(expNode.get("duration").asText())
                    .responsibilities(mapper.convertValue(expNode.get("responsibilities"), new TypeReference<List<String>>() {}))
                    .build();
            experiences.add(experience);
        }
        log.info("Saving the data in Dynamo DB");
		saveCandidate(candidate);
		saveExperiences(experiences);


	}

	public void saveCandidate(Candidate candidate) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("candidateId", AttributeValue.fromS(candidate.getCandidateId()));
		item.put("name", AttributeValue.fromS(candidate.getName()));
		item.put("email", AttributeValue.fromS(candidate.getEmail()));
		item.put("phone", AttributeValue.fromS(candidate.getPhone()));
		item.put("totalExp", AttributeValue.fromS(candidate.getTotalExp()));
		item.put("skills", AttributeValue.fromL(candidate.getSkills().stream().map(AttributeValue::fromS).toList()));
		item.put("location", AttributeValue.fromS(candidate.getLocation()));

		PutItemRequest request = PutItemRequest.builder()
				.tableName("CandidateDetails")
				.item(item)
				.build();

        log.info("Saving the Candidate information in Dynamo DB");

		dynamoDbClient.putItem(request);
	}

	public void saveExperiences(List<Experience> experiences) {
		for (Experience exp : experiences) {
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("experienceId", AttributeValue.fromS(exp.getExperienceId()));
			item.put("candidateId", AttributeValue.fromS(exp.getCandidateId()));
			item.put("company", AttributeValue.fromS(exp.getCompany()));
			item.put("position", AttributeValue.fromS(exp.getPosition()));
			item.put("duration", AttributeValue.fromS(exp.getDuration()));
			item.put("responsibilities", AttributeValue.fromL(exp.getResponsibilities().stream().map(AttributeValue::fromS).toList()));

			PutItemRequest request = PutItemRequest.builder()
					.tableName("ExperienceDetails")
					.item(item)
					.build();

            log.info("Saving the Experience information in Dynamo DB");

			dynamoDbClient.putItem(request);
		}

	}
}

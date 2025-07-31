package com.app.resumeanalyzer.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.app.resumeanalyzer.model.Candidate;
import com.app.resumeanalyzer.model.Experience;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class ResumeService {
	
	 private final S3Client s3Client = S3Client.create();
	 
	 String resumeText;
	 
	 @Value("${openapi-prompt}")
	 String prompt;
	 
	 @Value("${openapi-key}")
	 String apikey;
	 
	 private final RestTemplate restTemplate;
	 
	 public ResumeService(RestTemplate restTemplate, DynamoDbClient dynamoDbClient) {
	        this.restTemplate = restTemplate;
			this.dynamoDbClient = dynamoDbClient;
	    }
	 
	 private final DynamoDbClient dynamoDbClient;

	public void handleResumeUpload(String bucket, String key) throws IOException, TikaException {
	
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
		
		 ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(request);
		 
		  resumeText = extractTextFromFile(s3Object);
		  
		  String jsonResponse = sendToOpenAI(resumeText);
		
		  
	}

	private String sendToOpenAI(String resumeText) {
		
		  String url = "https://api.openai.com/v1/chat/completions";
		
		Map<String, Object> requestBody = new HashMap<>();
		
		requestBody.put("model", "gpt-3.5-turbo");
		
		 List<Map<String, String>> messages = new ArrayList<>();
	        messages.add(Map.of("role", "system", "content", prompt));
	        messages.add(Map.of("role", "user", "content", resumeText));
	        requestBody.put("messages", messages);
	        
	        requestBody.put("messages", messages);

	        requestBody.put("temperature", 0.2);
	        
	        HttpHeaders headers = new HttpHeaders();
	        
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.setBearerAuth(apikey);

	        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

	        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
	        
	        ObjectMapper mapper = new ObjectMapper();
	        
	        JsonNode root = mapper.readTree(response);
	        
	        String candidateId = UUID.randomUUID().toString();
	        
	        Candidate candidate = Candidate.builder()
	        	    .candidateId(candidateId)
	        	    .name(root.get("name").asText())
	        	    .email(root.get("email").asText())
	        	    .phone(root.get("phone").asText())
	        	    .skills(mapper.convertValue(root.get("skills"), new TypeReference<List<String>>() {}))
	        	    .location(root.get("location").asText())
	        	    .build();
	        
	        List<Experience> experiences = new ArrayList<>();
	        for (JsonNode expNode : root.get("experience")) {
	            Experience experience = Experience.builder()
	                .experienceId(UUID.randomUUID().toString())
	                .candidateId(candidateId)
	                .company(expNode.get("company").asText())
	                .position(expNode.get("position").asText())
	                .duration(expNode.get("duration").asText())
	                .responsibilities(mapper.convertValue(expNode.get("responsibilities"), new TypeReference<List<String>>() {}))
	                .build();
	            experiences.add(experience);
	        }

	        saveCandidate(candidate);
	        saveExperiences(experiences);
		
		return null;
	}

	  public void saveCandidate(Candidate candidate) {
	        Map<String, AttributeValue> item = new HashMap<>();
	        item.put("candidateId", AttributeValue.fromS(candidate.getCandidateId()));
	        item.put("name", AttributeValue.fromS(candidate.getName()));
	        item.put("email", AttributeValue.fromS(candidate.getEmail()));
	        item.put("phone", AttributeValue.fromS(candidate.getPhone()));
	        item.put("skills", AttributeValue.fromL(candidate.getSkills().stream().map(AttributeValue::fromS).toList()));
	        item.put("location", AttributeValue.fromS(candidate.getLocation()));

	        PutItemRequest request = PutItemRequest.builder()
	            .tableName("CandidateDetails")
	            .item(item)
	            .build();

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

	            dynamoDbClient.putItem(request);
	        }

	private String extractTextFromFile(ResponseInputStream<GetObjectResponse> s3Object) throws IOException, TikaException {
		Tika tika = new Tika();
		return tika.parseToString(s3Object);
		
	}
	
	

}

package com.app.resumeanalyzer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {
	
	@Bean
	public DynamoDbClient dynamoDbClient() {
		return DynamoDbClient.builder().region(Region.AF_SOUTH_1).build();
	}
	

}

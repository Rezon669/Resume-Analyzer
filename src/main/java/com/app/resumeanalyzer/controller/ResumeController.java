package com.app.resumeanalyzer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.app.resumeanalyzer.service.ResumeService;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {
	
	
	private static ResumeService resumeService;
	
	public ResumeController(ResumeService resumeService) {
		this.resumeService = resumeService;
	}
	
	@PostMapping("/process")
	public ResponseEntity<String> resumeParser(@RequestBody S3EventNotification eventNotification) {
		eventNotification.getRecords().forEach(record -> {
		String bucket = record.getS3().getBucket().getName();
		String key = record.getS3().getObject().getKey();
			resumeService.handleResumeUpload(bucket, key);
		});
		return ResponseEntity.ok("Resume processed");
		
	}

}

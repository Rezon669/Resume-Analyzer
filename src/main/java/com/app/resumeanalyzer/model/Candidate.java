package com.app.resumeanalyzer.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {
    private String candidateId;
    private String name;
    private String email;
    private String phone;
    private String location;
    private List<String> skills;
    
}
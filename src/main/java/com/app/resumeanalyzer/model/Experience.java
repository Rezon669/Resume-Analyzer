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
public class Experience {
    private String experienceId;
    private String candidateId;
    private String company;
    private String position;
    private String duration;
    private List<String> responsibilities;
}


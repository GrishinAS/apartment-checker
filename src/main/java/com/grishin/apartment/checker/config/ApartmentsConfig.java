package com.grishin.apartment.checker.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "apartments")
public class ApartmentsConfig {
    private List<CommunityConfig> communities;
    private String url;
    private Integer checkIntervalMinutes;
}
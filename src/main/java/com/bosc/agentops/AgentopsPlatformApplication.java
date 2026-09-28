package com.bosc.agentops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.bosc.agentops.**.mapper")
@ConfigurationPropertiesScan
public class AgentopsPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentopsPlatformApplication.class, args);
    }
}

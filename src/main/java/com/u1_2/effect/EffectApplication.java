package com.u1_2.effect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EffectApplication {

	static void main(String[] args) {
		SpringApplication.run(EffectApplication.class, args);
	}

}

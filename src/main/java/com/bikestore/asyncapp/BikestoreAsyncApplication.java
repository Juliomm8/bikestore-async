package com.bikestore.asyncapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.bikestore.asyncapp.config.ProcessProfileValidator;

@SpringBootApplication
public class BikestoreAsyncApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BikestoreAsyncApplication.class);
		application.addInitializers(context -> ProcessProfileValidator.validate(
				context.getEnvironment().getActiveProfiles()));
		application.run(args);
	}

}

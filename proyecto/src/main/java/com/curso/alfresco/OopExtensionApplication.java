package com.curso.alfresco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;

// El starter REST arrastra Spring Security; lo excluimos para no proteger una app que no expone endpoints.
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class OopExtensionApplication {

    public static void main(String[] args) {
        SpringApplication.run(OopExtensionApplication.class, args);
    }
}

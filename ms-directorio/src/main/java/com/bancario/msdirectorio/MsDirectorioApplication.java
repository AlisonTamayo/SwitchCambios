package com.bancario.msdirectorio;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class MsDirectorioApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsDirectorioApplication.class, args);
	}

	@PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println(">>> MS-DIRECTORIO iniciado en zona horaria: " + TimeZone.getDefault().getID());
    }

}

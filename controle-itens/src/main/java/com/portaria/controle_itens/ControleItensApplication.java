package com.portaria.controle_itens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ControleItensApplication {

	public static void main(String[] args) {
		SpringApplication.run(ControleItensApplication.class, args);
	}

}

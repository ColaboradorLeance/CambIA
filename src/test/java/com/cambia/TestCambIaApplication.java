package com.cambia;

import org.springframework.boot.SpringApplication;

public class TestCambIaApplication {

	public static void main(String[] args) {
		SpringApplication.from(CambIaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

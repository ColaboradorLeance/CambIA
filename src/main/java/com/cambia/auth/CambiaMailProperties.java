package com.cambia.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cambia.mail")
public record CambiaMailProperties(boolean habilitado, String remetente) {
}

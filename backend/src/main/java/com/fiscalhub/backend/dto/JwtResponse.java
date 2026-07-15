package com.fiscalhub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String email;
    private String nome;

    public JwtResponse(String token, Long id, String email, String nome) {
        this.token = token;
        this.id = id;
        this.email = email;
        this.nome = nome;
    }
}

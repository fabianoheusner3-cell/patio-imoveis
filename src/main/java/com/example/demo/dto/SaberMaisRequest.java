package com.example.demo.dto;

/** O ID obrigatorio vem da URL; imovelId no JSON e opcional e deve coincidir. */
public record SaberMaisRequest(String nome, String telefone, String email,
        String mensagem, Integer imovelId) {}
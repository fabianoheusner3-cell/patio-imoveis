package com.example.demo.models;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter @Setter
public class UsuarioModel {
    // CORRETOR (Aula 3): aceita ofertas e prepara os contratos que o ADMIN aprova.
    public enum Perfil { ADMIN, USER, CORRETOR }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    @Column(nullable = false, unique = true)
    private String email;
    @JsonIgnore
    @Column(nullable = false)
    private String senha;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;
}

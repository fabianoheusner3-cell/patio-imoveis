package com.example.demo.models;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Etapas (Aula 3): EM_PREPARACAO (corretor aceitou a oferta) -> AGUARDANDO_APROVACAO (corretor enviou)
 * -> AGUARDANDO_ASSINATURA (administrador aprovou) -> ASSINADO (cliente assinou). CANCELADO encerra.
 */
@Entity @Table(name="contratos") @Getter @Setter
public class ContratoModel {
    public enum Status { EM_PREPARACAO, AGUARDANDO_APROVACAO, AGUARDANDO_ASSINATURA, ASSINADO, CANCELADO }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(unique=true) private OfertaModel oferta;
    // Corretor que aceitou a oferta; so ele prepara o contrato.
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne private UsuarioModel corretor;
    private String pdf;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status=Status.EM_PREPARACAO;
    // Termos preenchidos pelo corretor
    @Column(precision=19,scale=2) private BigDecimal valorFinal;
    private LocalDate dataInicio;
    private Integer prazoMeses;
    @Column(length=4000) private String condicoes;
    // Pedido de ajuste do administrador ao devolver o contrato
    @Column(length=1000) private String motivoDevolucao;
    private Instant dataDevolucao;
    // Linha do tempo
    private Instant dataCriacao;
    private Instant dataPreparacao;
    private Instant dataAprovacao;
    private Instant dataEnvio;
    private Instant dataAssinatura;
    private Instant dataCancelamento;
    private String assinante;
}

package com.example.demo.models;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="contratos") @Getter @Setter
public class ContratoModel {
    public enum Status { AGUARDANDO_ASSINATURA, ASSINADO, CANCELADO }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(unique=true) private OfertaModel oferta;
    private String pdf;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status=Status.AGUARDANDO_ASSINATURA;
    private Instant dataAssinatura;
    private Instant dataAprovacao;
    private Instant dataEnvio;
    private String assinante;
}

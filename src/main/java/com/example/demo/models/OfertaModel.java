package com.example.demo.models;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="ofertas") @Getter @Setter
public class OfertaModel {
    public enum Tipo { ALUGUEL, VENDA }
    public enum Status { PENDENTE, ACEITA, EM_ESPERA, RECUSADA, CANCELADA }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private ImovelModel imovel;
    @ManyToOne(optional=false) private UsuarioModel usuario;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Tipo tipo;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal valorProposto;
    @Column(length=4000) private String mensagem;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status=Status.PENDENTE;
    @Column(nullable=false) private Instant dataCriacao=Instant.now();
}

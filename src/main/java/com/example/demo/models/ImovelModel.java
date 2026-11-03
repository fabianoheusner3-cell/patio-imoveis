package com.example.demo.models;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "imoveis")
@Getter
@Setter
public class ImovelModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String titulo;
    @Column(length = 4000)
    private String descricao;
    @Column(precision = 19, scale = 2)
    private BigDecimal precoVenda;
    @Column(precision = 19, scale = 2)
    private BigDecimal precoAluguel;
    private String finalidade;
    private String status;
    private Integer dormitorios;
    private Integer banheiros;
    private Integer garagem;
    @Column(precision = 19, scale = 2)
    private BigDecimal areaTotal;
    @Column(precision = 19, scale = 2)
    private BigDecimal areaConstruida;
    private String endereco;
    private String cidade;
    private String numero;
    private String complemento;
    private String cep;
    @Column(length = 4000)
    private String caracteristicas;
    private Boolean destaque = false;
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_imovel_id")
    private TipoImovelModel tipoImovel;
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bairro_id")
    private BairroModel bairro;
}

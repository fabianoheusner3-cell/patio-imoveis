package com.example.demo.models;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "fotos_imoveis")
@Getter
@Setter
public class FotoImovelModel implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "imovel_id", nullable = false)
    @JsonIgnoreProperties({"tipoImovel", "bairro", "hibernateLazyInitializer", "handler"})
    private ImovelModel imovel;
    private String nomeArquivo;
    private String caminho;
    private Boolean capa;
    private Integer ordem;

    public FotoImovelModel() {}

    @Override
    public int hashCode() {
        return 31 + (id == null ? 0 : id.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FotoImovelModel other = (FotoImovelModel) obj;
        return id != null && id.equals(other.id);
    }
}

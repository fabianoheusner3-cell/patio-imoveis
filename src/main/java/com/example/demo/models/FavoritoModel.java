package com.example.demo.models;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="favoritos",uniqueConstraints=@UniqueConstraint(columnNames={"usuario_id","imovel_id"})) @Getter @Setter
public class FavoritoModel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private UsuarioModel usuario;
    @ManyToOne(optional=false) private ImovelModel imovel;
}

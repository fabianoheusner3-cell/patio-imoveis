package com.example.demo.repositories;
import com.example.demo.models.OfertaModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
public interface OfertaRepository extends JpaRepository<OfertaModel,Long> {
    List<OfertaModel> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);
    List<OfertaModel> findByUsuarioId(Long usuarioId);
    List<OfertaModel> findByImovelId(Integer id);
    boolean existsByImovelId(Integer id);
    boolean existsByUsuarioIdAndImovelIdAndStatusIn(Long usuarioId,Integer imovelId,Collection<OfertaModel.Status> status);

    // Lista paginada (Pageable) com filtro de situacao e busca por imovel, codigo ou cliente.
    // usuario = null traz as ofertas de todos (corretor e administrador).
    // O grafo lista tambem tipo e bairro do imovel: o que fica fora dele vira lazy e o JSON falharia.
    @EntityGraph(attributePaths = {"imovel", "imovel.tipoImovel", "imovel.bairro", "usuario"})
    @Query("""
            select o from OfertaModel o join o.imovel i join o.usuario u
            where (:usuario is null or u.id = :usuario)
              and o.status in :situacoes
              and lower(concat(coalesce(i.titulo, ''), ' ', coalesce(u.nome, ''), ' ', coalesce(u.email, ''),
                    ' #', cast(i.id as String), ' #', cast(o.id as String)))
                  like lower(concat('%', :busca, '%'))
            """)
    Page<OfertaModel> buscar(@Param("usuario") Long usuario,
            @Param("situacoes") Collection<OfertaModel.Status> situacoes,
            @Param("busca") String busca, Pageable pageable);
}

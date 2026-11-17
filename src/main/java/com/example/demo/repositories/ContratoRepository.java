package com.example.demo.repositories;
import com.example.demo.models.ContratoModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
public interface ContratoRepository extends JpaRepository<ContratoModel,Long> {
    List<ContratoModel> findByOfertaUsuarioId(Long usuarioId);
    List<ContratoModel> findByCorretorId(Long corretorId);
    boolean existsByOfertaImovelIdAndStatusNot(Integer id,ContratoModel.Status status);

    // Lista paginada: cliente ve os proprios contratos, corretor os que ele prepara, admin todos.
    @Query("""
            select c from ContratoModel c join c.oferta o join o.imovel i join o.usuario u left join c.corretor k
            where (:usuario is null or u.id = :usuario)
              and (:corretor is null or k.id = :corretor)
              and c.status in :situacoes
              and lower(concat(coalesce(i.titulo, ''), ' ', coalesce(u.nome, ''), ' ', coalesce(k.nome, ''),
                    ' #', cast(c.id as String), ' #', cast(i.id as String)))
                  like lower(concat('%', :busca, '%'))
            """)
    Page<ContratoModel> buscar(@Param("usuario") Long usuario, @Param("corretor") Long corretor,
            @Param("situacoes") Collection<ContratoModel.Status> situacoes,
            @Param("busca") String busca, Pageable pageable);
}

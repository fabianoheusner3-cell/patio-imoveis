package com.example.demo.repositories;
import com.example.demo.models.OfertaModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OfertaRepository extends JpaRepository<OfertaModel,Long> {
    List<OfertaModel> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);
    List<OfertaModel> findByImovelId(Integer id);
    boolean existsByImovelId(Integer id);
    boolean existsByUsuarioIdAndImovelIdAndStatus(Long usuarioId,Integer imovelId,OfertaModel.Status status);
}

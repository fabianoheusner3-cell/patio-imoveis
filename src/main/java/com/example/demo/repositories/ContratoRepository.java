package com.example.demo.repositories;
import com.example.demo.models.ContratoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ContratoRepository extends JpaRepository<ContratoModel,Long> {
    List<ContratoModel> findByOfertaUsuarioId(Long usuarioId);
    boolean existsByOfertaImovelIdAndStatusNot(Integer id,ContratoModel.Status status);
}

package com.example.demo.repositories;
import com.example.demo.models.FavoritoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface FavoritoRepository extends JpaRepository<FavoritoModel,Long> {
    List<FavoritoModel> findByUsuarioId(Long id);
    Optional<FavoritoModel> findByUsuarioIdAndImovelId(Long usuarioId,Integer imovelId);
    void deleteByImovelId(Integer id);
}

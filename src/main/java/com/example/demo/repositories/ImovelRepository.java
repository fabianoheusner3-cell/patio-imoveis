package com.example.demo.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.models.ImovelModel;

public interface ImovelRepository extends JpaRepository<ImovelModel, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from ImovelModel i where i.id = :id")
    Optional<ImovelModel> bloquearPorId(@org.springframework.data.repository.query.Param("id") Integer id);

    // A API precisa dos relacionamentos antes de encerrar a transacao.
    @Override
    @EntityGraph(attributePaths = {"tipoImovel", "bairro"})
    List<ImovelModel> findAll();

    @Override
    @EntityGraph(attributePaths = {"tipoImovel", "bairro"})
    Page<ImovelModel> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"tipoImovel", "bairro"})
    Optional<ImovelModel> findById(Integer id);
}
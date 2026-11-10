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

    // Vitrine paginada no servidor: so imoveis disponiveis, com os filtros opcionais da tela.
    // Sem finalidade, o preco minimo/maximo vale para venda OU aluguel.
    @EntityGraph(attributePaths = {"tipoImovel", "bairro"})
    @org.springframework.data.jpa.repository.Query("""
            select i from ImovelModel i left join i.bairro b
            where lower(i.status) in ('disponível', 'disponivel')
              and (:bairro is null or b.id = :bairro)
              and lower(concat(coalesce(i.titulo, ''), ' ', coalesce(i.cidade, ''), ' ', coalesce(b.nome, '')))
                  like lower(concat('%', :busca, '%'))
              and ((:finalidade <> 'ALUGUEL' and i.precoVenda > 0
                    and (:min is null or i.precoVenda >= :min) and (:max is null or i.precoVenda <= :max))
                or (:finalidade <> 'VENDA' and i.precoAluguel > 0
                    and (:min is null or i.precoAluguel >= :min) and (:max is null or i.precoAluguel <= :max)))
            """)
    Page<ImovelModel> buscarVitrine(
            @org.springframework.data.repository.query.Param("bairro") Integer bairro,
            @org.springframework.data.repository.query.Param("busca") String busca,
            @org.springframework.data.repository.query.Param("finalidade") String finalidade,
            @org.springframework.data.repository.query.Param("min") java.math.BigDecimal min,
            @org.springframework.data.repository.query.Param("max") java.math.BigDecimal max,
            Pageable pageable);
}
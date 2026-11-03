package com.example.demo.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.models.BairroModel;
public interface BairroRepository extends JpaRepository<BairroModel, Integer> {}

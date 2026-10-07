package uy.edu.utec.iiss.engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uy.edu.utec.iiss.engine.model.Lectura;

@Repository
public interface LecturaRepository extends JpaRepository<Lectura, Long> {
}

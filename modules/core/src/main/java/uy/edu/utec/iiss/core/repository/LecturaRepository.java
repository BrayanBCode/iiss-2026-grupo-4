package uy.edu.utec.iiss.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uy.edu.utec.iiss.core.model.Lectura;

@Repository
public interface LecturaRepository extends JpaRepository<Lectura, Long> {
}

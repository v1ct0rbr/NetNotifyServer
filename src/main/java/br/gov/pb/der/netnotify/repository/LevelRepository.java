package br.gov.pb.der.netnotify.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.gov.pb.der.netnotify.model.Level;

public interface LevelRepository extends JpaRepository<Level, Integer> {

}

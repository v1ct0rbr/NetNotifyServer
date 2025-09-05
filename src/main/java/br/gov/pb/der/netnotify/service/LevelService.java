package br.gov.pb.der.netnotify.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import br.gov.pb.der.netnotify.model.Level;
import br.gov.pb.der.netnotify.repository.LevelRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class LevelService implements AbstractService<Level, Integer> {

    private final LevelRepository levelRepository;

    @Override
    public void save(Level entity) {
        levelRepository.save(entity);
    }

    @Override
    public void delete(Level entity) {
        levelRepository.delete(entity);
    }

    @Override
    @Cacheable(value="levels", key="#id")
    public Level findById(Integer id) {
        return levelRepository.findById(id).orElse(null);
    }

    @Override
    @Cacheable(value="levels", key="'all_levels'")
    public List<Level> findAll() {
        return levelRepository.findAll();
    }

}

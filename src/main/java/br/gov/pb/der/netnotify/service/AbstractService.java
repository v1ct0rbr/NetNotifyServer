package br.gov.pb.der.netnotify.service;

import java.util.List;

public interface AbstractService<T, ID> {

    public void save(T entity);

    public void delete(T entity);

    public T findById(ID id);

    public List<T> findAll();
}

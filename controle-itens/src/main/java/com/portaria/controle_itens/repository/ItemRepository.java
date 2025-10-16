package com.portaria.controle_itens.repository;

import com.portaria.controle_itens.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    // métodos custom se necessário
}

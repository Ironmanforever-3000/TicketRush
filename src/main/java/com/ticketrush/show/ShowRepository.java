package com.ticketrush.show;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    @EntityGraph(attributePaths = {"event", "venue"})
    Page<Show> findAll(Pageable pageable);
}

package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {
    void deleteByPage(PageEntity page);

    @Query("SELECT i FROM IndexEntity i " +
            "JOIN i.lemma l " +
            "WHERE i.page IN :pages " +
            "AND l.lemma IN :lemmas " +
            "ORDER BY l.frequency ASC")
    List<IndexEntity> findByPagesAndLemmas(
            @Param("pages") List<PageEntity> pages,
            @Param("lemmas") List<String> lemmas
    );
}

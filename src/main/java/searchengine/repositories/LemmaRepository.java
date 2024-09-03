package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

    Optional<LemmaEntity> findByLemmaAndSite(String lemma, SiteEntity site);

    long countBySite(SiteEntity site);

    List<LemmaEntity> findByLemmaIn(List<String> lemmas);

    int countByLemma(String lemma);
}

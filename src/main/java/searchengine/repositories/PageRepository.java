package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    long countBySite(SiteEntity site);

    PageEntity findByPathAndSite(String path, SiteEntity site);

    @Query("SELECT p " +
            "FROM PageEntity p " +
            "JOIN p.indices i " +
            "JOIN i.lemma l " +
            "WHERE l.lemma IN :lemmas " +
            "AND p.site.url = :siteUrl " +
            "GROUP BY p.id " +
            "HAVING COUNT(DISTINCT l.lemma) = :lemmaCount")
    List<PageEntity> findPagesByLemmasAndSite(
            @Param("lemmas") List<String> lemmas,
            @Param("siteUrl") String siteUrl,
            @Param("lemmaCount") int lemmaCount
    );
}

package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;


@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    boolean existsByPathAndSite(String path, SiteEntity site);
    long countBySite(SiteEntity site);
    PageEntity findByPathAndSite(String path, SiteEntity site);
}

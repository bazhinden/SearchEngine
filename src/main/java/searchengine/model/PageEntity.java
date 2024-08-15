package searchengine.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "page", indexes = @Index(name = "idx_path", columnList = "path"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"path", "site_id"}))
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)  // Site_ID: Это внешний ключ, который указывает на ID записи в таблице site.
    // Он показывает, к какому сайту относится каждая страница.
    //Пример: Строка с ID 1 в таблице page имеет Site_ID 1, что означает, что эта страница относится к сайту с ID 1 в таблице site.
    private SiteEntity site;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL)
    private List<IndexEntity> indices;

    @Enumerated(EnumType.STRING)
    private Status status;
}

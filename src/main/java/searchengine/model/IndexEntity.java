package searchengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "index_link")
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false) //Page_ID: Это внешний ключ, который указывает на ID записи в таблице page.
    // Он показывает, к какой странице относится каждая запись индекса.
    //Пример: Строка с ID 1 в таблице index_table имеет Page_ID 1, что означает, что этот индекс относится к странице с ID 1 в таблице page.
    private PageEntity page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)// Lemma_ID: Это внешний ключ, который указывает на ID записи в таблице lemma.
    // Он показывает, к какой лемме относится каждая запись индекса.
    //Пример: Строка с ID 1 в таблице index_table имеет Lemma_ID 1, что означает, что этот индекс относится к лемме с ID 1 в таблице lemma.
    private LemmaEntity lemma;

    @Column(name = "ranking", nullable = false)
    private Float ranking;
}

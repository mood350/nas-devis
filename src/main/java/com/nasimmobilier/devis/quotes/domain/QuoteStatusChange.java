package com.nasimmobilier.devis.quotes.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Historique des transitions de statut (append-only). */
@Entity
@Table(name = "quote_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteStatusChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quote quote;
    @Enumerated(EnumType.STRING)
    private Quote.Status fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Quote.Status toStatus;
    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    QuoteStatusChange(Quote quote, Quote.Status from, Quote.Status to) {
        this.quote = quote;
        this.fromStatus = from;
        this.toStatus = to;
    }
}

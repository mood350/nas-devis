package com.nasimmobilier.devis.quotes;

import org.springframework.test.context.ActiveProfiles;

/** Contrat SQL/transactionnel sur H2 (mode PostgreSQL). Ne prouve PAS la compatibilite PostgreSQL reelle. */
@ActiveProfiles("test")
class QuoteDbContractH2Test extends QuoteDbContractTest {
}

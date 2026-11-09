# Nasim Immobilier - Microservice de devis BTP

Backend Spring Boot 4.1.2 / Java 21 : catalogue de matériaux, fournisseurs, observations de prix, moteur de sélection
des prix (médiane), devis éditables, exports PDF (Thymeleaf + OpenHTMLtoPDF) et DOCX (Apache POI).

> **Sécurité : l'authentification et l'autorisation ne sont PAS encore implémentées.**
> Tous les endpoints sont ouverts. Ne pas exposer ce service tel quel sur Internet.

## Lancement
```
cp .env.example .env            # puis renseigner DB_PASSWORD (jamais committé : .env est dans .gitignore)
docker compose up --build       # PostgreSQL 16 + application sur http://localhost:8080
```
Sans Docker : un PostgreSQL accessible via `DB_URL` (optionnel), `DB_USER` et `DB_PASSWORD` (**obligatoires**, aucune valeur par défaut), puis `./mvnw spring-boot:run`.
Swagger UI : http://localhost:8080/swagger-ui.html - OpenAPI : `/v3/api-docs`.

**Données de démonstration** (catalogue fictif : ciment, fers Ø8-16, sable, gravier, parpaings, coffrage, PVC, câbles) :
`SPRING_PROFILES_ACTIVE=demo`. Tous les prix sont **fictifs** (`demo = true`) ; les devis qui les utilisent sont marqués
« DOCUMENT DE DÉMONSTRATION » et **ne peuvent pas être émis**. Sans ce profil, aucune donnée de démonstration n'existe.

## Tests
```
./mvnw clean verify
```
| Suite | Cible |
|---|---|
| `PricingEngineTest`, `PriceSelectorTest` | calcul et sélection des prix (purs) |
| `QuoteFlowTest` | API complète sur H2 : taxes, PRICE_UNAVAILABLE, conversion d'unités, édition de brouillon, immutabilité, pagination, PDF (multipage, accents) et DOCX, Swagger |
| `DemoCatalogTest` | profil demo |
| `QuoteDbContractH2Test` | transactions, rollback, verrou optimiste, numérotation concurrente, contraintes SQL, snapshots, sur H2 (mode PostgreSQL) |
| `QuoteDbContractPostgresTest` | **même contrat sur un vrai PostgreSQL 16 (Testcontainers)**. Ignoré automatiquement si Docker est absent |

> Sans Docker, la compatibilité PostgreSQL **n'est pas vérifiée** : seul H2 est exercé. Voir « État de la vérification » ci-dessous.

## Endpoints (préfixe `/api`)
Toutes les listes sont paginées : `page`, `size` (1-100), `sort=champ,asc|desc` (liste blanche, sinon 400), `q` (recherche).
Réponse : `{content, page, size, totalElements, totalPages}`.

| Ressource | Endpoints |
|---|---|
| Catégories, Fournisseurs, Unités | `GET/POST /categories`, `/suppliers`, `/units` ; `PUT/DELETE /{id}` |
| Conversions d'unités | `GET/POST /unit-conversions` |
| Matériaux | `GET /materials` (filtres `categoryId`, `active`, `q`), `GET/PUT /materials/{id}`, `POST /materials` |
| Observations de prix | `POST/GET /price-observations` (filtres `materialId`, `supplierId`, `locality`, `status`, `sourceType`), `POST /price-observations/{id}/validate`, `/reject` |
| Prix d'un matériau | `GET /materials/{id}/prices` (historique), `GET /materials/{id}/price-resolution?locality=&date=&sourceType=` |
| Taxes | `GET/POST /tax-rates`, `POST /tax-rates/{code}/validate` |
| Devis | `POST/GET /quotes`, `GET/PUT/DELETE /quotes/{id}`, `POST /quotes/{id}/lines`, `PUT/DELETE /quotes/{id}/lines/{lineId}`, `POST /quotes/{id}/reprice`, `POST /quotes/{id}/issue` |
| Modèles | `GET/POST /quote-templates`, `DELETE /quote-templates/{id}`, `POST /quote-templates/{id}/quotes` |
| Exports | `GET /quotes/{id}/pdf`, `GET /quotes/{id}/docx` |

Erreurs RFC 9457 : 400 validation, 404 introuvable, 409 doublon/référence/modification concurrente, 422 règle métier
(`code: PRICE_UNAVAILABLE` quand aucun prix fiable n'existe).

## Règles de prix
Le prix d'une ligne issue du catalogue est la **médiane** des observations `COMMERCIAL` **validées**, de même localité
(= « localisation du chantier » du devis), mêmes caractéristiques, unité convertible, non périmées (`max-observation-age-days`,
90 par défaut), non futures. Si moins de `min-observations` (2) sont retenues : `PRICE_UNAVAILABLE`, jamais de prix inventé.
Le résultat indique la confiance (`HIGH/MEDIUM/LOW`), les observations utilisées et les exclusions avec leur motif.
Les prix `ADMINISTRATIVE_REFERENCE` sont stockés séparément et jamais mélangés aux prix commerciaux. Détails et diagramme : `docs/ARCHITECTURE.md`.

| Propriété | Défaut |
|---|---|
| `app.pricing.max-observation-age-days` | 90 |
| `app.pricing.min-observations` | 2 |
| `app.pricing.require-same-locality` | true |
| `app.pricing.include-demo` | false (true avec le profil `demo`) |
| `app.pricing.high-confidence-min-observations` / `-max-spread-percent` | 3 / 25 |
| `app.quote.max-discount-percent` | 100 |

## Fiscalité
**Aucune taxe n'est appliquée par défaut** et aucun taux n'est préchargé. Créer un taux (`POST /api/tax-rates`), le valider
(`POST /api/tax-rates/{code}/validate`), puis le référencer explicitement : `taxCode` par ligne ou pour le devis, `transportTaxCode`
pour le transport. Les taux utilisés, leurs bases et montants sont figés dans le devis. Le devis distingue HT, remise, transport, net HT, taxes, TTC.
Ordre : lignes → sous-total → remise (avant taxe) → transport (non remisé) → taxes par taux → TTC.

## Devis
`DRAFT` : modifiable (en-tête, lignes, quantités, `reprice`). `ISSUED` : immuable. Historique des statuts dans `history`.
`reprice` re-sélectionne les prix des lignes catalogue (prix manuels conservés) ; sans cet appel, un brouillon garde ses prix figés.

## Dépendances ajoutées (par rapport au POM initial)
| Dépendance | Raison |
|---|---|
| spring-boot-starter-validation | validation des DTO |
| spring-boot-starter-flyway, flyway-database-postgresql | migrations `db/migration` (`db/demo` pour le profil demo) |
| poi-ooxml 5.4.1 | export DOCX |
| openhtmltopdf-pdfbox 1.0.10 | export PDF depuis HTML/CSS |
| h2 (test) | tests d'intégration sans Docker |
| testcontainers-postgresql, testcontainers-junit-jupiter (test) | tests sur PostgreSQL réel |

Spring Boot 4.1.2 avec springdoc 3.2.0.
PostgreSQL est la seule base ; `sqlite-jdbc` reste dans le POM sans être configuré. `spring-boot-starter-mail` est conservé, sans envoi de mail.

## Migrations
`V1__init` (schéma de base), `V2__price_observations` (observations, conversions d'unités, caractéristiques ; remplace `price_entries`),
`V3__taxes_and_quote_snapshots` (taxes, snapshots de devis, historique), `db/demo/V900__demo_catalog` (profil demo).

Voir `docs/ARCHITECTURE.md` pour l'architecture, les diagrammes et les garanties ACID.

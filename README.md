# Nasim Immobilier - Microservice de devis BTP

Backend Spring Boot 4.1.2 / Java 21 : catalogue de matériaux, fournisseurs, observations de prix, moteur de sélection
des prix (médiane), devis éditables, exports PDF (Thymeleaf + OpenHTMLtoPDF) et DOCX (Apache POI).

> **Sécurité : l'authentification et l'autorisation ne sont PAS encore implémentées.**
> Tous les endpoints sont ouverts. Ne pas exposer ce service tel quel sur Internet.

## Lancement
Les variables `DB_USER` et `DB_PASSWORD` sont **obligatoires** (aucune valeur par défaut). Si l'une est absente ou vide,
l'application s'arrête au démarrage avec un message qui nomme la variable manquante.

### Avec Docker Compose
```
cp .env.example .env            # puis renseigner DB_PASSWORD (jamais committé : .env est dans .gitignore)
docker compose up --build       # PostgreSQL 16 + application sur http://localhost:8080
```
Le fichier `.env` est lu **par Docker Compose** pour renseigner les `${...}` de `docker-compose.yml`. Le compose force
`DB_USER=nasim` et `DB_URL` vers le conteneur `db`.

### Depuis IntelliJ IDEA (ou `./mvnw spring-boot:run`)
Spring Boot **ne charge pas** le fichier `.env`. Dans IntelliJ : *Run → Edit Configurations → NasimmobilierApplication →
Environment variables* (activer le champ via *Modify options* si besoin) :
```
DB_USER=mon_user;DB_PASSWORD=mon_mot_de_passe;DB_URL=jdbc:postgresql://localhost:5432/nasimmobilier
```
Avec Maven en terminal, exporter les variables dans le shell avant `./mvnw spring-boot:run`. Il faut un PostgreSQL local
dont la base existe déjà (Flyway crée les tables, pas la base).

Swagger UI : http://localhost:8080/swagger-ui.html - OpenAPI : `/v3/api-docs`.

### Variables d'environnement
| Variable | Rôle | Obligatoire | Exemple / défaut |
|---|---|---|---|
| `DB_USER` | utilisateur PostgreSQL | **oui** | `nasim` |
| `DB_PASSWORD` | mot de passe PostgreSQL | **oui** | (secret, à ne jamais committer) |
| `DB_URL` | URL JDBC | non | `jdbc:postgresql://localhost:5432/nasimmobilier` |
| `COMPANY_NAME` / `COMPANY_ADDRESS` | identité sur les devis | non | `NASIM IMMOBILIER` / `Lome, Togo` |
| `COMPANY_NIF` / `COMPANY_PHONE` / `COMPANY_EMAIL` | mentions légales et contact sur les devis | non | vides par défaut |
| `COMPANY_LOGO` | logo (ressource Spring) | non | `classpath:logo.png` (placeholder à remplacer) |
| `PRICING_MAX_AGE_DAYS` | âge max d'une observation de prix | non | `90` |
| `PRICING_MIN_OBSERVATIONS` | observations minimales pour un prix | non | `2` |
| `PRICING_REQUIRE_SAME_LOCALITY` | exiger la même localité | non | `true` |
| `QUOTE_MAX_DISCOUNT_PERCENT` | remise maximale d'un devis (%) | non | `100` |
| `SPRING_PROFILES_ACTIVE` | `demo` charge le catalogue fictif | non | vide |

Les autres propriétés `app.pricing.*` (`include-demo`, `high-confidence-*`) se règlent dans `application.properties`
(voir « Règles de prix »).

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
| `QuoteDbContractPostgresTest` | **même contrat (6 tests) sur un vrai PostgreSQL 16 via Testcontainers** |
| `DatabaseCredentialsCheckTest` | échec du démarrage si `DB_USER` / `DB_PASSWORD` sont absents ou vides |

**Tests PostgreSQL (Testcontainers)** : ils s'exécutent dès qu'un Docker est joignable, en local comme en CI. Sans Docker
local ils sont **ignorés** (`disabledWithoutDocker = true`) : Maven les affiche alors comme « skipped » et seul H2 est exercé.
Le workflow GitHub Actions (`.github/workflows/ci.yml`, job `build`, `ubuntu-latest` où Docker est présent) lance
`./mvnw -B clean verify` à chaque PR vers `main`, donc ces 6 tests y tournent ; le job `build` est exigé avant fusion.

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
| Modèles | `GET/POST /quote-templates` (liste paginée, tri `id`/`name`, `q` sur le nom), `DELETE /quote-templates/{id}`, `POST /quote-templates/{id}/quotes` |
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
La suppression d'un brouillon (`DELETE /quotes/{id}`) et celle d'un modèle sont **douces** : la ligne reste en base (`deleted_at`) avec ses lignes et son historique, mais n'apparaît plus dans l'API (404). Il n'existe pas de restauration par l'API. Le nom d'un modèle supprimé est suffixé pour être de nouveau utilisable.
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
PostgreSQL est la seule base (H2 sert uniquement aux tests).

## Migrations
`V1__init` (schéma de base), `V2__price_observations` (observations, conversions d'unités, caractéristiques ; remplace `price_entries`),
`V3__taxes_and_quote_snapshots` (taxes, snapshots de devis, historique), `V4__soft_delete` (colonne `deleted_at`), `V5__price_observation_version` (verrou optimiste des observations), `V6__backfill_legacy_quote_totals` (totaux des devis antérieurs à V3), `db/demo/V900__demo_catalog` (profil demo).

Voir `docs/ARCHITECTURE.md` pour l'architecture, les diagrammes et les garanties ACID.

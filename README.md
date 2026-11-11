# Nasim Immobilier — devis BTP

Un service web (API) pour **préparer des devis de chantier**.
Vous gardez un catalogue de matériaux et les prix relevés chez vos fournisseurs. Le service choisit un prix fiable, calcule la remise, les taxes et le total, puis exporte le devis en **PDF** ou en **Word (DOCX)**.

> ⚠️ **Pas d'authentification.** Tous les endpoints sont ouverts : n'exposez pas ce service tel quel sur Internet.

**Sommaire** : [Démarrer](#démarrer-en-3-minutes) · [Premier devis](#votre-premier-devis) · [Comment ça marche](#comment-ça-marche) · [Endpoints](#endpoints) · [Configuration](#configuration) · [Développer et tester](#développer-et-tester)

---

## Démarrer en 3 minutes

Il faut seulement [Docker](https://docs.docker.com/get-docker/).

```bash
cp .env.example .env        # puis ouvrez .env et choisissez un mot de passe : DB_PASSWORD=...
docker compose up --build   # PostgreSQL + l'application
```

Ouvrez ensuite **http://localhost:8080/swagger-ui.html** : c'est une page où l'on voit tous les endpoints et où l'on peut les essayer (bouton *Try it out*).

**Envie d'essayer avec des données ?** Dans `.env`, mettez `SPRING_PROFILES_ACTIVE=demo`. Un catalogue **fictif** est alors chargé (ciment, fers, sable, gravier, parpaings, PVC, câbles). Les devis qui utilisent ces prix portent la mention « DOCUMENT DE DÉMONSTRATION » et **ne peuvent pas être émis**.

---

## Votre premier devis

### Le plus simple : des lignes saisies à la main

```bash
curl -X POST http://localhost:8080/api/quotes -H "Content-Type: application/json" -d '{
  "clientName": "M. Koffi Agbodjan",
  "projectTitle": "Mur de cloture",
  "siteLocation": "Lome",
  "lines": [
    { "designation": "Main d oeuvre macon", "unit": "f", "quantity": 1, "unitPrice": 5000 }
  ]
}'
```

> Sous Windows, un terminal peut mal envoyer les accents (erreur `400`) : l'exemple n'en contient pas. Pour des textes accentués, utilisez plutôt Swagger UI.

La réponse contient l'`id` du devis, son numéro (`DEV-2026-000001`) et le total. Avec cet `id` (ici `1`) :

| Je veux… | J'ouvre / j'appelle |
|---|---|
| télécharger le PDF | http://localhost:8080/api/quotes/1/pdf (dans le navigateur) |
| télécharger le Word | http://localhost:8080/api/quotes/1/docx |
| ajouter une ligne | `POST /api/quotes/1/lines` |
| **émettre** le devis (il ne sera plus modifiable) | `POST /api/quotes/1/issue` |

> Aucune taxe n'est appliquée tant que vous n'en avez pas créé une (voir [Taxes](#taxes)).

### Avec les prix du catalogue

Au lieu de `designation` / `unit` / `unitPrice`, une ligne peut désigner un matériau : `{ "materialId": 3, "quantity": 10 }`. Le service retrouve alors le prix tout seul, à partir des relevés de prix validés (voir [Prix](#le-prix-dun-matériau)).

Avec le profil `demo`, cherchez un matériau (`GET /api/materials?q=ciment`), notez son `id`, et utilisez-le dans une ligne.

---

## Comment ça marche

```
 Matériaux ──┐
             ├─►  prix choisi (médiane)  ─►  lignes du devis  ─►  DEVIS BROUILLON  ─►  DEVIS ÉMIS  ─►  PDF / Word
 Relevés de  ┘    des relevés validés        (copie figée)         modifiable          figé pour toujours
 prix validés
```

### Le prix d'un matériau
Un **relevé de prix** dit : « ce fournisseur vend ce matériau tant, dans cette ville, à cette date ». Un relevé est créé en attente (`PENDING`), puis **validé à la main** (`POST /api/price-observations/{id}/validate`). Seuls les relevés validés comptent.

Le prix d'une ligne est la **médiane** des relevés qui :
- sont validés, de type `COMMERCIAL`,
- viennent de la **même ville** que le chantier (`siteLocation`),
- ont les **mêmes caractéristiques** que le matériau,
- ont une unité convertible vers celle du matériau,
- ne sont ni futurs ni vieux de plus de **90 jours**.

Il en faut au moins **2**. Sinon la réponse est une erreur `422` avec `code: PRICE_UNAVAILABLE` : **le service n'invente jamais un prix**. Une confiance accompagne chaque prix :

| Confiance | Quand |
|---|---|
| `HIGH` | au moins 3 relevés, au moins 2 fournisseurs, écart max/min ≤ 25 % de la médiane |
| `MEDIUM` | au moins 2 relevés venant d'au moins 2 fournisseurs |
| `LOW` | le reste (par exemple un seul fournisseur) |

Les prix `ADMINISTRATIVE_REFERENCE` (barèmes officiels) sont gardés à part et ne sont **jamais mélangés** aux prix commerciaux.

**Unités** : si un fournisseur vend en tonnes un matériau compté en kg, déclarez la conversion (`POST /api/unit-conversions` : 1 t = 1000 kg). Le sens inverse est déduit : ne le déclarez pas une seconde fois.

### Les calculs d'un devis
1. total d'une ligne = quantité × prix unitaire
2. sous-total HT = somme des lignes
3. remise (en %) appliquée **avant** la taxe
4. transport ajouté **après** la remise (jamais remisé)
5. taxe calculée par taux, sur chaque base
6. total TTC = net HT + taxes

Tous les montants sont arrondis à 2 décimales et affichés tels quels sur les PDF/Word.

### Taxes
**Aucun taux n'existe par défaut.** Pour taxer : créer un taux (`POST /api/tax-rates`), le **valider** (`POST /api/tax-rates/{code}/validate`), puis l'indiquer par son code (`taxCode` sur une ligne ou sur tout le devis, `transportTaxCode` pour le transport).

### Vie d'un devis
- **Brouillon (`DRAFT`)** : on modifie l'en-tête, les lignes et les quantités. Les prix restent figés jusqu'à `POST /quotes/{id}/reprice`, qui recalcule les prix du catalogue (les prix saisis à la main sont conservés). Changer le chantier d'un brouillon refait ce calcul automatiquement.
- **Émis (`ISSUED`)** : le devis ne change plus jamais, même si le catalogue ou les prix évoluent (le prix, les taxes et l'identité de l'entreprise sont copiés dans le devis).
- Supprimer un brouillon ou un modèle est une **suppression douce** : il disparaît de l'API mais reste en base.
- **Deux personnes sur le même brouillon ?** Ajoutez `?version=<la version lue dans le devis>` à une modification ou à l'émission : si quelqu'un est passé avant vous, vous recevez `409` au lieu d'écraser son travail.

### Erreurs
Format standard (RFC 9457) : `400` requête invalide · `404` introuvable · `409` doublon ou modification simultanée · `422` règle métier non respectée (devis émis, prix indisponible, remise trop forte…).

---

## Endpoints

Tout est sous `/api`. Les listes sont paginées : `page`, `size` (1 à 100), `sort=champ,asc|desc`, `q` (recherche). Pour tout voir et essayer : [Swagger UI](http://localhost:8080/swagger-ui.html).

| Ressource | Endpoints |
|---|---|
| Catégories, fournisseurs, unités | `GET/POST /categories`, `/suppliers`, `/units` · `PUT/DELETE /{id}` |
| Conversions d'unités | `GET/POST /unit-conversions` |
| Matériaux | `GET/POST /materials` · `GET/PUT /materials/{id}` |
| Relevés de prix | `GET/POST /price-observations` · `POST /price-observations/{id}/validate` ou `/reject` |
| Prix d'un matériau | `GET /materials/{id}/price-resolution?locality=Lomé` (prix choisi et explication) · `GET /materials/{id}/prices` (historique) |
| Taxes | `GET/POST /tax-rates` · `POST /tax-rates/{code}/validate` |
| Devis | `GET/POST /quotes` · `GET/PUT/DELETE /quotes/{id}` · `POST /quotes/{id}/lines` · `PUT/DELETE /quotes/{id}/lines/{lineId}` · `POST /quotes/{id}/reprice` · `POST /quotes/{id}/issue` |
| Modèles de devis | `GET/POST /quote-templates` · `DELETE /quote-templates/{id}` · `POST /quote-templates/{id}/quotes` (crée un devis depuis le modèle) |
| Exports | `GET /quotes/{id}/pdf` · `GET /quotes/{id}/docx` |

---

## Configuration

Deux variables sont **obligatoires** : `DB_USER` et `DB_PASSWORD` (aucune valeur par défaut ; sans elles l'application refuse de démarrer et dit laquelle manque). Les autres sont facultatives.

| Variable | Rôle | Défaut |
|---|---|---|
| `DB_USER`, `DB_PASSWORD` | accès à PostgreSQL | **obligatoires** |
| `DB_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/nasimmobilier` |
| `COMPANY_NAME`, `COMPANY_ADDRESS`, `COMPANY_PHONE`, `COMPANY_EMAIL`, `COMPANY_NIF` | identité imprimée sur les devis | `NASIM IMMOBILIER`, `Lome, Togo`, vides |
| `COMPANY_LOGO` | logo PNG | `classpath:logo.png` (un exemple à remplacer) |
| `PRICING_MAX_AGE_DAYS` | âge maximum d'un relevé de prix | `90` |
| `PRICING_MIN_OBSERVATIONS` | nombre minimum de relevés | `2` |
| `PRICING_REQUIRE_SAME_LOCALITY` | exiger la même ville | `true` |
| `QUOTE_MAX_DISCOUNT_PERCENT` | remise maximale d'un devis (%) | `100` |
| `SPRING_PROFILES_ACTIVE` | `demo` charge le catalogue fictif | vide |

**Avec Docker Compose**, ces variables se mettent dans le fichier `.env` (jamais versionné). Le compose fixe lui-même `DB_USER=nasim` et `DB_URL`. L'application tourne sans droits root dans son conteneur, avec un contrôle de santé intégré (`docker ps` affiche `healthy`).

**Sans Docker** (IntelliJ ou `./mvnw spring-boot:run`) : Spring Boot **ne lit pas** `.env`.
- IntelliJ : *Run → Edit Configurations → NasimmobilierApplication → Environment variables* : `DB_USER=...;DB_PASSWORD=...`
- Terminal : exportez les variables avant `./mvnw spring-boot:run`.

Il faut alors un PostgreSQL local dont la base `nasimmobilier` existe déjà (le service crée les tables, pas la base).

---

## Développer et tester

```bash
./mvnw clean verify     # compile et lance tous les tests (c'est ce que fait le CI)
./mvnw test -Dtest=QuoteFlowTest    # un seul test
```

- Les tests tournent sur une base H2 en mémoire : aucun Docker nécessaire.
- 6 tests supplémentaires tournent sur un **vrai PostgreSQL 16** (Testcontainers). Sans Docker ils sont **ignorés** (« skipped ») ; le CI, lui, les exécute.
- La branche `main` est protégée : passer par une pull request, le contrôle CI `build` doit être vert.
- Le schéma de la base est géré par des migrations (`src/main/resources/db/migration`). Ne modifiez jamais une migration déjà appliquée : ajoutez-en une nouvelle.

Pour l'architecture, les diagrammes et les garanties de transaction : [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

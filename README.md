# GitHub Repositories Reviewer (Java 21 + Spring Boot 3.5)

Usługa HTTP, która dla zadanego użytkownika GitHuba zwraca listę **nie‑forkowanych** repozytoriów oraz – dla każdego repo – listę **gałęzi** z ostatnim **commit SHA**. Dla nieistniejącego użytkownika zwraca **404** w wymaganym formacie.

---

## Spis treści

* [Wymagania](#wymagania)
* [Szybki start](#szybki-start)
* [Konfiguracja](#konfiguracja)
* [API](#api)
* [Przykłady](#przykłady)
* [Obsługa błędów](#obsługa-błędów)
* [Test integracyjny](#test-integracyjny)
* [Decyzje projektowe i założenia](#decyzje-projektowe-i-założenia)
* [Struktura projektu](#struktura-projektu)
* [Uruchomienie w Eclipse](#uruchomienie-w-eclipse)

---

## Wymagania

* **Java 21**
* **Maven 3.9+** (lub Gradle, jeśli używasz wariantu Gradle)

---

## Szybki start

### 1) Klon/zip

```bash
git clone https://github.com/marekrynarzewski/githubreviewer
cd githubreviewer
```

### 2) (Opcjonalnie) token do GitHuba — większy limit zapytań

Ustaw zmienną środowiskową `GITHUB_TOKEN` (fine‑grained, read‑only do repo):

* **Linux/macOS**

  ```bash
  export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxx
  ```
* **Windows PowerShell**

  ```powershell
  $env:GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxxx"
  ```

> W Eclipse: **Run → Run Configurations… → Spring Boot App → Environment → New…** →
> `Name=GITHUB_TOKEN`, `Value=ghp_xxx`.

### 3) Uruchom aplikację

```bash
./mvnw spring-boot:run
```
# albo:
```bash
./mvnw clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Aplikacja startuje domyślnie pod `http://localhost:8080`.

---

## Konfiguracja

`application.yml`

```yaml
server:
  port: 8080

github:
  api:
    base: https://api.github.com
  token: ${GITHUB_TOKEN:}   # opcjonalnie; jeśli puste, żądania będą anonimowe
```

Nagłówki wysyłane do GitHuba:

* `Accept: application/vnd.github+json`
* `User-Agent: repo-lister`
* (jeśli ustawiono token) `Authorization: Bearer <token>`

---

## API

### GET `/github/{username}/repos`

Zwraca listę repozytoriów dla `{username}`, **odfiltrowując forki**. Dla każdego repozytorium zwraca gałęzie i `lastCommitSha`.

**Response 200 – schema**

```json
[
  {
    "repositoryName": "string",
    "ownerLogin": "string",
    "branches": [
      { "name": "string", "lastCommitSha": "string" }
    ]
  }
]
```

**Response 404 – schema** (dla nieistniejącego użytkownika)

```json
{ "status": 404, "message": "Not Found" }
```

> **Uwaga:** Brak paginacji – zgodnie z wymaganiami zwracamy tylko to, co zwróci domyślna strona GitHuba.

---

## Przykłady

### Istniejący użytkownik

```bash
curl -s http://localhost:8080/github/torvalds/repos | jq .
```

Przykładowy fragment:

```json
[
  {
    "repositoryName": "linux",
    "ownerLogin": "torvalds",
    "branches": [
      { "name": "master", "lastCommitSha": "e3c1a9..." }
    ]
  }
]
```

### Nieistniejący użytkownik

```bash
curl -i http://localhost:8080/github/this-user-does-not-exist-xyz/repos
```

Oczekiwane:

```
HTTP/1.1 404
Content-Type: application/json
...
{"status":404,"message":"Not Found"}
```

---

## Obsługa błędów

* **404 Not Found** – użytkownik GitHuba nie istnieje (format jak wyżej).
* **429/403 z GitHuba** (rate‑limit) – zalecane ustawienie `GITHUB_TOKEN`.
* **5xx** – błędy infrastrukturalne (np. GitHub chwilowo niedostępny).

Aplikacja posiada globalny handler błędów zwracający `application/json` o postaci:

```json
{ "status": <httpStatus>, "message": "<reason>" }
```

---

## Test integracyjny

W projekcie znajduje się **jeden test integracyjny „happy path”**, który uruchamia pełny kontekst Springa i stubuje GitHuba (WireMock):

* Stub: `GET /users/{user}/repos` → lista repo (w tym jeden `fork=true`, który zostanie odfiltrowany).
* Stub: `GET /repos/{owner}/{repo}/branches` → lista gałęzi z `commit.sha`.
* Asercje: endpoint `/github/{user}/repos` zwraca wyłącznie nie‑forki oraz poprawne `name`/`lastCommitSha`.

Uruchomienie testów:

```bash
./mvnw test
```

---

## Decyzje projektowe i założenia

* **Java 21 / Spring Boot 3.5**.
* **HTTP klient:** `RestClient` (Spring 6.2+), następca `RestTemplate`.
* **Brak WebFlux** (explicitnie zabroniony w zadaniu).
* **Brak DDD/Hexagonal** – prosta, czytelna struktura (controller → service → client).
* **Brak paginacji** – zarówno po stronie endpointu, jak i w konsumpcji GitHuba.
* **DTO jako `record`** – zwięzłe, niemutowalne obiekty transportowe.
* **Obsługa 404** – dokładny format `{"status":…, "message":…}`.
* **Rate limits** – rekomendowany `GITHUB_TOKEN`, ale aplikacja działa też bez niego.

**Celowo pominięto** (by nie wykraczać poza zakres zadania): metryki, logowanie korelacyjne, cache, Dockerfile, CI/CD, paginację, retry, circuit‑breakery itp.

---

## Struktura projektu

```
src/
  main/
    java/<root>/
      config/
        GitHubClientConfig.java            # bean RestClient.Builder
      controller/
        GitHubController.java              # GET /github/{username}/repos
      service/
        GitHubService.java                 # logika: filtr forków, pobranie gałęzi
      client/
        GithubClient.java                  # wywołania GitHub API v3
      dto/
        BranchResponse.java
        RepoResponse.java
        github/
          RepoDto.java
          BranchDto.java
      exception/
        NotFoundException.java
        ErrorPayload.java
        GlobalExceptionHandler.java
application.yml
  test/
    java/<root>/integration/
      GitHubIntegrationTest.java           # jeden test „happy path” (WireMock)
```

---

## Uruchomienie w Eclipse

1. **File → Import… →** (dla Mavena) **Existing Maven Projects** / (dla Gradle) **Existing Gradle Project**.
2. Wskaż katalog z `pom.xml` / `build.gradle` → **Finish**.
3. Upewnij się, że `src/main/java` jest **Source Folderem** (PPM → *Build Path → Use as Source Folder*).
4. Uruchom klasę główną z `@SpringBootApplication` (**Run As → Java Application**) lub **Run As → Maven build…** z `spring-boot:run`.
5. (Opcjonalnie) **Run Configurations… → Environment** → dodaj `GITHUB_TOKEN`.

---

### Źródła

* GitHub REST API v3 – dokumentacja: [https://docs.github.com/rest](https://docs.github.com/rest)

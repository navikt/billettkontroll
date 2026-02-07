# Billettkontroll

Applikasjon laget for å tilby SOAP-tjenesten erEgenAnsattEllerIFamilieMedEgenAnsatt til Infotrygd Facade. Denne
tjenesten ble tidligere tilbudt av bussen.

Kotlin-applikasjon som serverer en SOAP WebService med JDK HttpServer.

## Oversikt

- **SOAP Endpoint**: `/services/pipEgenAnsatt`
- **Health Check**: `/internal/health/liveness`

## Integrasjoner

Applikasjonen kaller [skjermede-personer-pip](https://github.com/navikt/skjermede-personer-pip) for å sjekke om en person er skjermet (egen ansatt).

| Miljø | URL |
|-------|-----|
| Dev | https://skjermede-personer-pip.intern.dev.nav.no |
| Prod | https://skjermede-personer-pip.intern.nav.no |

Autentisering skjer via Azure AD Client Credentials.

## Lokal utvikling

### Forutsetninger

- Java 25
- Maven 3.9+

### Bygg og kjør

```bash
./mvnw clean package -DskipTests
java -jar target/billettkontroll-1.0.0-SNAPSHOT.jar
```

### Test endpoints

```bash
# Health
curl http://localhost:8080/internal/health/liveness

# WSDL
curl http://localhost:8080/services/pipEgenAnsatt?wsdl
```

### Docker

```bash
./mvnw clean package -DskipTests
docker build -t billettkontroll:latest .
docker run -p 8080:8080 billettkontroll:latest
```

## NAIS Deploy

Deployes automatisk via GitHub Actions ved push til `main`:

1. Maven bygger applikasjonen (med cache)
2. Docker image bygges og pushes
3. Deploy til dev-gcp
4. Deploy til prod-gcp

## Miljøer

| Miljø | URL |
|-------|-----|
| Dev | https://billettkontroll.intern.dev.nav.no |
| Prod | https://billettkontroll.intern.nav.no |

## Teknologi

- Kotlin 2.3
- JDK 25 HttpServer
- Metro JAX-WS (SOAP)
- Logback (logging)
- Jackson (JSON)
- Azure AD (autentisering)

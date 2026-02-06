# Billettkontroll

Applikasjon laget for å tilby tjenesten SOAP tjensten erEgenAnsattEllerIFamilieMedEgenAnsatt til Infotrygd Facade. Denne
tjenesten ble tidligere tilbudt av bussen.s

Kotlin-applikasjon som serverer en SOAP WebService med JDK HttpServer.

## Oversikt

- **SOAP Endpoint**: `/services/pipEgenAnsatt`
- **Health Check**: `/internal/health/liveness`

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

| Miljø | URL | Replicas |
|-------|-----|----------|
| Dev | https://billettkontroll.intern.dev.nav.no | 2-4 |
| Prod | https://billettkontroll.intern.nav.no | 4-8 |

## Teknologi

- Kotlin 2.3
- JDK 25 HttpServer
- Metro JAX-WS (SOAP)
- Logback (logging)

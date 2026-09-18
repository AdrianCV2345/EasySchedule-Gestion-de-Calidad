# EasySchedule

**EasySchedule** es una plataforma integral de gestión académica diseñada para optimizar y automatizar el proceso de inscripción y planificación curricular en instituciones universitarias. Construido sobre una arquitectura robusta que integra un backend en Spring Boot y un cliente web en Angular, el sistema garantiza la integridad del historial del estudiante mediante la validación estricta de reglas de negocio en tiempo real. Esto incluye la verificación automática de prerrequisitos aprobados, el control de los límites máximos de créditos por semestre y la prevención de conflictos o duplicidad en la toma de materias.

Más allá del registro tradicional, la plataforma destaca por su enfoque en la experiencia del usuario y su capacidad para procesar estructuras académicas complejas, incluyendo un motor de asistencia para la estructuración de horarios eficientes. Al centralizar la administración de mallas curriculares, la oferta de materias y el seguimiento del avance estudiantil en una interfaz fluida e intuitiva, EasySchedule reduce significativamente la carga administrativa y empodera a los universitarios para gestionar su trayectoria educativa sin contratiempos.

---

# Estructura del Monorepo

- `backend/`: Spring Boot + Gradle
- `frontend/`: Angular

## Versiones recomendadas

- Java: `17`
- Node: `20.19.0`
- npm: `10.8.2`
- Angular CLI: `20.3.19`

## 1) Requisitos previos

Verifica versiones:

```bash
java -version
node -v
npm -v
```

## 2) Base de datos PostgreSQL

La app espera, por defecto:

- DB: `EasySchedule`
- Usuario: `postgres`
- Password: `postgres`
- Puerto: `5432`

Crea la base manualmente:

```sql
CREATE DATABASE "EasySchedule";
```

Variables disponibles (opcionales):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JPA_DDL_AUTO`
- `CORS_ALLOWED_ORIGINS`

Referencia: `backend/.env.example`

### Ejecutar schema + seeds

Al iniciar el backend, los seeds se ejecutan automaticamente solo si la base esta vacia (o si el esquema no existe).
Si ya hay datos, se omiten.

Tambien puedes ejecutarlos manualmente bajo demanda:

```bash
cd backend
chmod +x run-seeds.sh
./run-seeds.sh
```

Variables opcionales para el script:

- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `EasySchedule`)
- `DB_USER` (default: `postgres`)
- `DB_PASSWORD` (default: `postgres`)
- `WITH_SCHEMA` (default: `true`)

## 3) Levantar backend

```bash
cd backend
./gradlew bootRun
```

En Windows PowerShell, si `./gradlew` no funciona:

```powershell
.\gradlew.bat bootRun
```

Backend: `http://localhost:8080`

Aclaración: al iniciar el servicio, es normal que la consola de logs se detenga alrededor del 80%. Este comportamiento es propio de Spring Boot durante el arranque; no indica un error y el servicio ya se encuentra en ejecución.

Pruebas manuales de endpoints (HTTP files):

- `backend/src/main/resources/http/test.http`
- `backend/src/main/resources/http/estudiante.http`

## 4) Levantar frontend

```bash
cd frontend
npm ci
npm start
```

Frontend: `http://localhost:4200`

## 5) Verificaciones rapidas

```bash
cd backend
./gradlew test

cd ../frontend
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

## 6) Coverage

Backend (JaCoCo):

```bash
cd backend
./gradlew test jacocoTestReport
```

Frontend (Karma/Istanbul):

```bash
cd frontend
npm test -- --watch=false --browsers=ChromeHeadless --code-coverage
```

Reportes:

- Backend: `backend/build/reports/jacoco/test/html/index.html`
- Frontend: `frontend/coverage/frontend/index.html`

## 7) Análisis estático con SonarQube (local)

Requisitos:

- Servidor SonarQube corriendo en `http://localhost:9000`.
- Un token personal con permiso `Execute Analysis`.
- Java 17 o superior y el wrapper de Gradle del backend.

El token nunca debe escribirse en `build.gradle.kts`, `sonar-project.properties` ni confirmarse en Git. Cada desarrollador debe definirlo solo en su sesión local. La clave por defecto es `SIS-312_Practica1`; si se usa la misma clave, todos los análisis actualizan el mismo proyecto de SonarQube.

En Linux/macOS:

```bash
cd backend
export SONAR_TOKEN='TU_TOKEN_PERSONAL'
export SONAR_HOST_URL='http://localhost:9000'
export SONAR_PROJECT_KEY='SIS-312_Practica1'
export SONAR_PROJECT_NAME='SIS-312_Practica1'
./gradlew sonar
```

En Windows PowerShell:

```powershell
$env:SONAR_TOKEN = 'TU_TOKEN_PERSONAL'
$env:SONAR_HOST_URL = 'http://localhost:9000'
$env:SONAR_PROJECT_KEY = 'SIS-312_Practica1'
$env:SONAR_PROJECT_NAME = 'SIS-312_Practica1'
cd backend
.\gradlew.bat sonar
```

Para aislar completamente los reportes locales por desarrollador, usen una clave distinta (el proyecto debe existir o el token debe tener permiso para crearlo):

```powershell
$env:SONAR_PROJECT_KEY = "SIS-312_Practica1-$env:USERNAME"
$env:SONAR_PROJECT_NAME = "SIS-312_Practica1-$env:USERNAME"
.\gradlew.bat sonar
```

La configuración del análisis está en `backend/build.gradle.kts`; `backend/sonar-project.properties` conserva los valores estáticos compatibles con SonarScanner CLI. El task `sonar` ejecuta las pruebas y genera el reporte JaCoCo antes de publicar el análisis.

## 8) CI/CD con GitHub Actions

Se incluye el workflow `.github/workflows/ci-cd.yml` con estas reglas:

- En cada PR hacia `master`: ejecuta CI de backend y frontend.
- En cada push a `master`: ejecuta CI y, si todo pasa, dispara el deploy a producción.
- El job de deploy usa `environment: production` y falla si falla Netlify o Render.

### Qué bloquea el merge

Para que un PR no se pueda mergear si fallan validaciones, protege la rama `master` en GitHub y exige los checks del workflow.

Importante: el deploy ocurre después del merge, en el push a `master`. Por eso, el merge se bloquea por CI, mientras que cualquier falla de deploy queda visible en GitHub Actions y hace fallar el job `deploy`.

### Proteger la rama `master`

Configura en `Settings > Branches > Branch protection rules`:

- Regla para `master`.
- Activar `Require status checks to pass before merging`.
- Marcar como requeridos los checks del workflow:
	- `Backend CI (build + tests)`
	- `Frontend CI (test + build)`

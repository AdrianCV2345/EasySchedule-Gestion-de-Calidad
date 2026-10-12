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

El token nunca debe escribirse en `build.gradle.kts`, `sonar-project.properties` ni confirmarse en Git. Cada desarrollador debe definirlo solo en su sesión local. 

En Linux/macOS:

```bash
cd backend
export SONAR_TOKEN='TU_TOKEN_PERSONAL'
export SONAR_HOST_URL='http://localhost:9000'
export SONAR_PROJECT_KEY='NOMBRE_DE_TU_PROYECTO'
export SONAR_PROJECT_NAME='NOMBRE_DE_TU_PROYECTO'
./gradlew sonar
```

En Windows PowerShell:

```powershell
$env:SONAR_TOKEN = 'TU_TOKEN_PERSONAL'
$env:SONAR_HOST_URL = 'http://localhost:9000'
$env:SONAR_PROJECT_KEY = 'NOMBRE_DE_TU_PROYECTO'
$env:SONAR_PROJECT_NAME = 'NOMBRE_DE_TU_PROYECTO'
cd backend
.\gradlew.bat sonar
```

Para aislar completamente los reportes locales por desarrollador, usen una clave distinta (el proyecto debe existir o el token debe tener permiso para crearlo):

```powershell
$env:SONAR_PROJECT_KEY = "NOMBRE_DE_TU_PROYECTO-$env:USERNAME"
$env:SONAR_PROJECT_NAME = "NOMBRE_DE_TU_PROYECTO-$env:USERNAME"
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

---

## 9) Marco de Calidad de Software: PoC de Frameworks de Pruebas Unitarias (Sprint Quality Assurance)

### Objetivo del Sprint
Establecer y estandarizar el marco de calidad de software del backend en EasySchedule mediante una **Prueba de Concepto (PoC)** comparativa entre los dos motores principales de testing en el ecosistema Java: **JUnit 5 (Jupiter)** y **TestNG**. 

Para evaluar rigurosamente ambos frameworks bajo condiciones del mundo real, se tomó como caso de estudio la **Historia de Usuario: Registro de Usuarios**, diseñando e implementando casos de prueba derivados de la **Complejidad Ciclomática** $V(G)$ de los componentes involucrados, garantizando la cobertura de caminos independientes (caminos base de McCabe):

1. **`AuthController.registerUser(...)`**: Complejidad ciclomática $V(G) = 4$ (3 puntos de decisión: 2 operadores ternarios de normalización/fallback para `username` y `email`, y 1 bloque `try-catch`).
   - *Camino C1:* Username no nulo, Email no nulo, servicio exitoso -> Retorna `201 CREATED`.
   - *Camino C2:* Username nulo (fallback cadena vacía `""`), Email válido, servicio exitoso -> Retorna `201 CREATED`.
   - *Camino C3:* Username válido, Email nulo (fallback cadena vacía `""`), servicio exitoso -> Retorna `201 CREATED`.
   - *Camino C4:* Invocación del servicio falla con excepción (`UserAlreadyExistsException`) -> Captura en bloque `catch`, registro en log de error y relanza la excepción.
2. **`AuthService.registerUser(...)`**: Complejidad ciclomática $V(G) = 3$ (2 puntos de decisión: condición `existsByUsernameIgnoreCase` y condición `existsByEmailIgnoreCase`).
   - *Camino S1:* Username ya existente -> Lanza `UserAlreadyExistsException` ("Error: El nombre de usuario ya está en uso").
   - *Camino S2:* Username disponible, Email ya existente -> Lanza `UserAlreadyExistsException` ("Error: El correo electrónico ya está registrado").
   - *Camino S3:* Username disponible, Email disponible -> Cifra contraseña con BCrypt y persiste el nuevo usuario en el repositorio (`userRepository.save`).

Total de caminos independientes cubiertos en ambas suites: **7 caminos** ($4 + 3$).

### Matriz Comparativa: JUnit 5 vs. TestNG

| Criterio de Evaluación | JUnit 5 (Jupiter) | TestNG | Conclusión / Impacto en el Proyecto |
| :--- | :--- | :--- | :--- |
| **Integración con Spring Boot** | **Nativa y de primer nivel** (`@SpringBootTest`, `@WebMvcTest`, `@MockitoBean`, `SpringExtension`). Viene preconfigurado en `spring-boot-starter-test`. | Requiere configuración manual de listeners (`TestContextManager`) o herencia de clases base propietarias de Spring. | JUnit 5 ofrece cero fricción al integrar tests unitarios y de slice web en Spring Boot 3. |
| **Integración con Mockito** | **Extensión nativa moderna** (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`). | Requiere inicialización manual imperativa (`MockitoAnnotations.openMocks(this)`) en hooks `@BeforeMethod` / `@AfterMethod`. | JUnit 5 simplifica el ciclo de vida y previene fugas de mocks de manera declarativa. |
| **Estructuración y Aserciones** | Soporte para `@Nested`, `@DisplayName`, y compatibilidad total con **AssertJ** (`assertThat`, `assertThatThrownBy`). | No soporta `@Nested`. Aserciones nativas con sintaxis inversa (`assertEquals(actual, expected)`) o `@Test(expectedExceptions)`. | JUnit 5 provee una jerarquía de pruebas BDD clara, expresiva y legible para QA. |
| **Soporte de Gradle** | Nativo de primer nivel mediante `useJUnitPlatform()`. Ejecución paralela integrada sin plugins externos. | Requiere bloque explícito `useTestNG()`, configuración de suites XML o configuración manual de classpath en tasks personalizados. | El soporte nativo de Gradle para JUnit Platform es el estándar de facto. |
| **Compatibilidad con JaCoCo** | **100% nativo**. Agentes y ejecución `.exec` alineados sin configuración adicional. | Compatible, pero requiere asociar tareas específicas de ejecución al reporte de JaCoCo. | Ambos generan cobertura, pero JUnit 5 se integra transparentemente con pipelines CI/CD y SonarQube. |

> **Decisión Técnica:** Se selecciona formalmente **JUnit 5** como el framework estándar oficial para todas las pruebas del backend de EasySchedule debido a su integración nativa con Spring Boot 3.x, arquitectura modular, soporte con Mockito y compatibilidad sin fricciones en pipelines de CI/CD.

### Comandos de Ejecución y Cobertura

Para ejecutar las suites de pruebas y generar los reportes de cobertura en el backend, utiliza los siguientes comandos desde la raíz del proyecto o desde el directorio `backend`:

#### 1. Ejecutar Pruebas Unitarias Oficiales (JUnit 5):
```bash
# Desde la raíz del proyecto
./backend/gradlew -p backend test

# O ingresando al directorio backend
cd backend
./gradlew test
```
*En Windows PowerShell:* `.\gradlew.bat test`

#### 2. Ejecutar Suite Comparativa PoC (TestNG):
```bash
# Desde la raíz del proyecto
./backend/gradlew -p backend testNg

# O ingresando al directorio backend
cd backend
./gradlew testNg
```
*En Windows PowerShell:* `.\gradlew.bat testNg`

#### 3. Generar Reporte de Cobertura JaCoCo (Excluyendo clases de configuración y clase principal):
```bash
# Desde la raíz del proyecto
./backend/gradlew -p backend test jacocoTestReport

# O ingresando al directorio backend
cd backend
./gradlew test jacocoTestReport
```
*En Windows PowerShell:* `.\gradlew.bat test jacocoTestReport`

#### Ruta del Reporte de Cobertura Generado:
Una vez ejecutada la tarea, abre el reporte interactivo HTML en tu navegador:
- **Ruta HTML:** `backend/build/reports/jacoco/test/html/index.html`
- **Ruta XML (para SonarQube/CI):** `backend/build/reports/jacoco/test/jacocoTestReport.xml`

# TASK — Create a Hermetic Multi-Stage Docker Build for the GraphHopper Matrix Server

*(FINAL UPDATED VERSION — Fully Consistent, Zero Host Dependencies, Single Source of Truth)*

You are GitHub Copilot.  
Your goal is to generate ALL Docker-related artifacts required to build and run a **complete GraphHopper + Matrix API** Java server using a **hermetic, reproducible, multi-stage Docker build**.  
The user must **never** run Maven locally.  
Only `docker` / `docker-compose` should be required.

Below is the exact project structure. You MUST produce files that work with it.

---

## 1. PROJECT STRUCTURE (THIS IS EXACT — FOLLOW IT STRICTLY)

```
```
GEO/
├── devtools/
│   └── graphhopper-build/
│       ├── config.yml              # Dropwizard/GraphHopper config
│       ├── truck.json              # Custom truck profile
│       ├── Dockerfile              # YOU will generate
│       ├── docker-compose.yml      # YOU will generate
│       └── README.md               # YOU will generate
├── matrix-extension/
│   ├── src/
│   │   ├── main/
│   │   │   └── java/
│   │   │       └── com/
│   │   │           └── dynop/
│   │   │               └── graphhopper/
│   │   │                   └── matrix/
│   │   │                       ├── api/
│   │   │                       │   ├── MatrixRequest.java
│   │   │                       │   ├── MatrixResource.java
│   │   │                       │   └── MatrixResponse.java
│   │   │                       └── config/
│   │   │                           ├── MatrixBundle.java
│   │   │                           └── MatrixGraphHopperProvider.java
│   │   └── test/
│   │       └── java/
│   │           └── com/
│   │               └── dynop/
│   │                   └── graphhopper/
│   │                       └── matrix/
│   │                           └── api/
│   │                               └── MatrixResourceTest.java
│   ├── pom.xml                     # Maven module to be built inside Docker
│   └── target/                     # Shaded JAR will appear here
├── osm-data/
│   └── europe/
│       └── europe-latest.osm.pbf
└── pom.xml                         # (optional root POM)
```
```

The final runnable server main class MUST be:  
`com.dynop.graphhopper.matrix.MatrixServerApplication`.

---

## 2. MULTI-STAGE DOCKERFILE REQUIREMENTS

Create a **hermetic**, **reproducible**, **multi-stage Dockerfile** in:

`devtools/graphhopper-build/Dockerfile`

### Stage 1 — BUILDER (Maven)

- Base image: `maven:3.9-eclipse-temurin-21`
- WORKDIR: `/build`
- Copy both pom.xml files:
    - root pom.xml (if exists)
    - matrix-extension/pom.xml
- Run:

```bash
mvn -f matrix-extension/pom.xml dependency:go-offline
```

(Critical for caching)

- Copy entire `matrix-extension/src/`
- Run full build INSIDE Docker:

```bash
mvn -f matrix-extension/pom.xml clean package -DskipTests
```

- Output JAR must be the shaded (fat) jar under:  
    `matrix-extension/target/*.jar`

### Stage 2 — RUNTIME (JRE)

- Base image: `eclipse-temurin:21-jre`
- WORKDIR: `/app`
- Copy shaded JAR from Stage 1 → `/app/app.jar`
- Copy config:

```dockerfile
COPY devtools/graphhopper-build/config.yml /app/config.yml
```

- Declare volumes:

```dockerfile
VOLUME /app/osm
VOLUME /app/graph-cache
```

- EXPOSE port **8080**
- ENTRYPOINT:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar", "server", "config.yml"]
```

---

## 3. DOCKER-COMPOSE REQUIREMENTS

Generate file at:

`devtools/graphhopper-build/docker-compose.yml`

```yaml
services:
    gh-matrix:
        build:
            context: ../../        # must see matrix-extension source root
            dockerfile: devtools/graphhopper-build/Dockerfile
        container_name: gh-matrix
        volumes:
            - ../../osm-data/europe:/app/osm
            - ./graph-cache:/app/graph-cache
        ports:
            - "8080:8080"
        environment:
            - JAVA_OPTS=-Xmx4g -Xms4g
        restart: unless-stopped
```

---

## 4. MAVEN SHADE PLUGIN REQUIREMENTS

In `matrix-extension/pom.xml`, generate or update shade plugin:

- Must create a runnable fat JAR
- Must merge all GraphHopper + Dropwizard dependencies
- Must specify main class:  
    `com.dynop.graphhopper.matrix.MatrixServerApplication`

Example required:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.dynop.graphhopper.matrix.MatrixServerApplication</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 5. README.md REQUIREMENTS (hermetic build = NO MAVEN LOCALLY)

Create the `devtools/graphhopper-build/README.md` with the correct, fully hermetic instructions. Detailed documentation is needed.

### Build & Run (no Maven, no Java on host)

```bash
cd devtools/graphhopper-build
docker-compose up --build
```

### Test the Matrix API:

```bash
curl -X POST http://localhost:8080/custom/matrix \
    -H "Content-Type: application/json" \
    -d '{
                "points": [[48.1,16.2],[48.2,16.3]],
                "profile": "truck",
                "metrics": ["distance","time"]
            }'
```

**Absolutely DO NOT include:**

```bash
mvn clean package
```

The multi-stage Docker build must compile everything.

---

## 6. FINAL DELIVERABLES (Copilot must output all)

- Multi-stage Dockerfile (builder + runner)
- docker-compose.yml
- Shade plugin configuration in pom.xml
- Create README.md with hermetic build workflow. Detailed documentation needed.
- Validation that MatrixBundle registers and `/custom/matrix` is active

### Your Output Must Be:

A complete, ready-to-run Dockerized GraphHopper + Matrix server that builds from source inside Docker and runs with one command:

```bash
docker-compose up --build
```
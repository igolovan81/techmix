# SonarQube Local Usage Guide

This guide explains how to set up and use SonarQube locally to analyze your codebase for quality and security issues.

---

## 1. What is SonarQube?
SonarQube is a popular tool for continuous inspection of code quality. It performs static code analysis to detect bugs, code smells, and security vulnerabilities in your projects.

---

## 2. Prerequisites
- **Java 11+** (required for running SonarQube server)
- **Docker** (optional, for running SonarQube via container)
- **Maven/Gradle** (for Java projects)

---

## 3. Running SonarQube Locally

### Option A: Using Docker (Recommended)
1. Add the following service to your `docker-compose.yml` (if not already present):

```yaml
sonarqube:
  image: sonarqube:community
  ports:
    - "9000:9000"
  environment:
    - SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true
  volumes:
    - sonarqube_data:/opt/sonarqube/data
    - sonarqube_logs:/opt/sonarqube/logs
    - sonarqube_extensions:/opt/sonarqube/extensions

volumes:
  sonarqube_data:
  sonarqube_logs:
  sonarqube_extensions:
```

2. Start SonarQube:
```sh
docker-compose up -d sonarqube
```

3. Access the dashboard at: [http://localhost:9000](http://localhost:9000)
   - Default credentials: `admin` / `admin`

### Option B: Manual Installation
1. Download SonarQube Community Edition from [https://www.sonarqube.org/downloads/](https://www.sonarqube.org/downloads/)
2. Extract and run:
```sh
unzip sonarqube-*.zip
cd sonarqube-*
./bin/<your-os>/sonar.sh start
```
3. Access the dashboard at: [http://localhost:9000](http://localhost:9000)

---

## 4. Analyzing Your Project

### Java (Maven) Example
1. Ensure you have a `sonar-project.properties` file in your project root (see example below).
2. Run the analysis:
```sh
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=<your_project_key> \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=<your_token>
```
- Generate a token in the SonarQube UI under `My Account > Security`.

### Example `sonar-project.properties`
```
sonar.projectKey=your_project_key
sonar.projectName=Your Project Name
sonar.sources=src/main/java
sonar.java.binaries=target/classes
```

---

## 5. Viewing Results
- Open [http://localhost:9000](http://localhost:9000) in your browser.
- Log in and select your project to view code quality reports, issues, and suggestions.

---

## 6. Troubleshooting
- If SonarQube fails to start, ensure no other service is using port 9000.
- Check logs in the `sonarqube_logs` volume or the `logs/` directory of your SonarQube installation.
- For Docker, use `docker-compose logs sonarqube` to view logs.

---

## 7. References
- [SonarQube Documentation](https://docs.sonarqube.org/)
- [SonarScanner for Maven](https://docs.sonarqube.org/latest/analysis/scan/sonarscanner-for-maven/)
- [SonarQube DockerHub](https://hub.docker.com/_/sonarqube)

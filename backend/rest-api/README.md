# TechMix Project

This README provides common instructions for building, testing, launching, and stopping the TechMix project.

---

## Project Structure
- `backend/rest-api/` — Java Spring Boot REST API
- Other directories — Frontend or additional services (if present)

---

## Prerequisites
- Java 11 or higher
- Maven
- Docker & Docker Compose (for containerized setup)

---

## 1. Build the Project

### Using Maven (Backend)
```sh
cd backend/rest-api
mvn clean install
```

### Using Docker Compose (All Services)
```sh
docker-compose build
```

---

## 2. Run the Project

### Using Maven (Backend Only)
```sh
cd backend/rest-api
mvn spring-boot:run
```

### Using Docker Compose (Recommended)
```sh
docker-compose up -d
```
This will start all defined services (backend, database, SonarQube, etc.) in the background.

---

## 3. Test the Project

### Backend Tests (Maven)
```sh
cd backend/rest-api
mvn test
```

---

## 4. Stop the Project

### If running with Docker Compose
```sh
docker-compose down
```

### If running with Maven (Backend Only)
- Press `Ctrl+C` in the terminal where the server is running.

---

## 5. Additional Resources
- [SONARQUBE.md](./SONARQUBE.md) — Guide for local SonarQube usage
- `backend/rest-api/README.md` — Backend-specific documentation

---

## 6. Troubleshooting
- Ensure required ports (e.g., 8080 for backend, 9000 for SonarQube) are free.
- Check logs for errors: `docker-compose logs` or backend logs in `backend/rest-api`.

---

## 7. Contact
For questions or issues, please contact the project maintainer.

# Docker Compose Usage Guide

This project uses Docker Compose to manage development and integration services. The following services are included:

- **SonarQube** (code quality)
- **H2 Database** (in-memory/testing)
- **Postgres Database** (relational database)
- **Oracle Database** (relational database)
- **Snyk CLI** (security scanning)

## Prerequisites
- [Docker](https://www.docker.com/get-started) and [Docker Compose](https://docs.docker.com/compose/) installed

## Usage

### Start All Services
```sh
docker-compose up -d
```

### Stop All Services
```sh
docker-compose down
```

### View Logs for a Service
```sh
docker-compose logs <service-name>
```
Example:
```sh
docker-compose logs postgres
```

## Service Details

### SonarQube
- Web UI: [http://localhost:9000](http://localhost:9000)

### H2 Database
- Web Console: [http://localhost:81](http://localhost:81)
- JDBC: `jdbc:h2:tcp://localhost:1521//opt/h2-data/test`
- Username: `sa`, Password: *(empty)*

### Postgres Database
- Host: `localhost`
- Port: `5432`
- Database: `appdb`
- Username: `appuser`
- Password: `apppassword`

### Oracle Database
- Host: `localhost`
- Port: `1522`
- Service/SID: `XEPDB1` (default for XE)
- Username: `appuser`
- Password: `apppassword`
- SYS/SYSTEM Password: `oraclepassword`

### Snyk CLI
- The Snyk CLI container is for local security scanning of your project.
- To use, set your Snyk token in a `.env` file or your shell:
  ```sh
  export SNYK_TOKEN=your_snyk_token
  ```
- Start the container, then exec into it:
  ```sh
  docker-compose exec snyk sh
  ```
- Run Snyk scans inside the container:
  ```sh
  snyk test
  snyk monitor
  ```
- The project directory is mounted at `/project`.

## Data Persistence
- Data for Postgres and Oracle is persisted in Docker volumes (`postgres_data`, `oracle_data`).
- H2 data is persisted in the container volume (`/opt/h2-data`).

## Troubleshooting
- If a service fails to start, check logs with `docker-compose logs <service-name>`.
- Make sure required ports are not in use by other applications.
- For database connection issues, verify credentials and host/port settings.
- For Snyk CLI, ensure your SNYK_TOKEN is set and valid.

---

For more details, see the individual service READMEs or documentation.

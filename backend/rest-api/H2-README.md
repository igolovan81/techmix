# Local H2 Database Usage

This project is configured to use an in-memory H2 database for local development and testing.

## Connection Details
- **JDBC URL:** `jdbc:h2:mem:testdb`
- **Driver Class:** `org.h2.Driver`
- **Username:** `sa`
- **Password:** *(empty)*

These settings are defined in `src/main/resources/application.properties`.

## Accessing the H2 Console
The H2 database provides a web-based console for inspecting and querying the database.

- **Console URL:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **JDBC URL:** `jdbc:h2:mem:testdb`
- **Username:** `sa`
- **Password:** *(leave blank)*

> The console is enabled by default for local development. If you change the port or context path, update the URL accordingly.

## Notes
- The H2 database is in-memory and will be reset each time the application restarts.
- Schema and data are initialized using Liquibase changelogs and any `schema.sql` or `data.sql` files present in `src/main/resources`.
- For persistent storage, configure a different database in `application.properties`.

## Troubleshooting
- If you cannot access the console, ensure the application is running and the following properties are set in `application.properties`:
  ```properties
  spring.h2.console.enabled=true
  spring.h2.console.path=/h2-console
  ```
- If you see a login error, double-check the JDBC URL and credentials.

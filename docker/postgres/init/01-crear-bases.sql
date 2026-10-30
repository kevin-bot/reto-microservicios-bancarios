-- Una base por microservicio (database-per-service), cada una con su propio usuario.
CREATE USER clientes_user WITH PASSWORD 'clientes_pass';
CREATE DATABASE clientes_db OWNER clientes_user;

CREATE USER cuentas_user WITH PASSWORD 'cuentas_pass';
CREATE DATABASE cuentas_db OWNER cuentas_user;

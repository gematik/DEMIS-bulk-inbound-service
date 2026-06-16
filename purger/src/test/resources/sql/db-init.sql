-- this user is required by liquibase changelog scripts. it is not used by test
CREATE USER "ars-bulk-user" WITH PASSWORD 'does-not-matter';

-- this user is used
CREATE USER "ars-bulk-purger" WITH PASSWORD 'topSecret' NOINHERIT;

-- required to insert rows for the test
GRANT postgres TO "ars-bulk-purger"

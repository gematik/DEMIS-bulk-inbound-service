package de.gematik.demis.bulk.inbound.purger.test;

/*-
 * #%L
 * surveillance-pseudonym-purger
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test-with-postgres")
@DirtiesContext
public abstract class TestWithPostgresContainer {

  public static final String DB_ADMIN_USER = "postgres";

  // Note: We do not use @ServiceConnection here, because we want to test the permissions of the
  // special purger db user
  @Container
  protected static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("demis-bulk")
          .withUsername(DB_ADMIN_USER)
          .withInitScript("sql/db-init.sql");

  @DynamicPropertySource
  protected static void postgresqlProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.liquibase.url", postgres::getJdbcUrl);
    registry.add("spring.liquibase.user", postgres::getUsername);
    registry.add("spring.liquibase.password", postgres::getPassword);
  }
}

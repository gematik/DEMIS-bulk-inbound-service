package de.gematik.demis.bulk.inbound.purger;

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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.bulk.inbound.purger.test.TestDataDAO;
import de.gematik.demis.bulk.inbound.purger.test.TestWithPostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"app.purger.retention-days=" + PurgerIntegrationSystemTest.RETENTION_DAYS})
@Import(TestDataDAO.class)
@Slf4j
class PurgerIntegrationSystemTest extends TestWithPostgresContainer {

  public static final int RETENTION_DAYS = 14;

  @Autowired PurgerApplication underTest;
  @Autowired TestDataDAO testDataDAO;

  @Test
  void deleteBatches() {
    final List<UUID> batchesToDelete =
        List.of(
            testDataDAO.insertBatch(cutOffDate().minus(1, ChronoUnit.DAYS), 0),
            testDataDAO.insertBatch(cutOffDate().minus(1, ChronoUnit.DAYS), 1),
            testDataDAO.insertBatch(cutOffDate().minus(1000, ChronoUnit.DAYS), 0),
            testDataDAO.insertBatch(cutOffDate().minus(1, ChronoUnit.SECONDS), 0));

    final List<UUID> batchesNotToDelete =
        List.of(
            testDataDAO.insertBatch(cutOffDate().plus(1, ChronoUnit.DAYS), 0),
            testDataDAO.insertBatch(cutOffDate().plus(1, ChronoUnit.DAYS), 1),
            testDataDAO.insertBatch(cutOffDate().plus(1, ChronoUnit.MINUTES), 0));

    underTest.run();

    assertThat(batchesToDelete)
        .isNotEmpty()
        .allSatisfy(
            batchId ->
                assertThat(testDataDAO.batchExists(batchId))
                    .as("Batch must be deleted.")
                    .isFalse());

    assertThat(batchesNotToDelete)
        .isNotEmpty()
        .allSatisfy(
            batchId ->
                assertThat(testDataDAO.batchExists(batchId))
                    .as("Batch must not be deleted.")
                    .isTrue());
  }

  private Instant cutOffDate() {
    return Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
  }
}

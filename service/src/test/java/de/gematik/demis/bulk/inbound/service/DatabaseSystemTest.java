package de.gematik.demis.bulk.inbound.service;

/*-
 * #%L
 * bulk-inbound-service
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

import static de.gematik.demis.bulk.inbound.service.service.BatchStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.gematik.demis.bulk.inbound.service.entity.BatchEntity;
import de.gematik.demis.bulk.inbound.service.messaging.InQueueMessageHandler;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.bulk.inbound.service.service.BatchStatus;
import de.gematik.demis.bulk.inbound.service.test.TestWithPostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-config")
@Slf4j
class DatabaseSystemTest extends TestWithPostgresContainer {

  @MockitoBean InQueueMessageHandler mockRabbitMqListener;
  @Autowired private BatchRepository batchRepository;

  private static BatchEntity createBatchEntity() {
    final BatchEntity entity = new BatchEntity();
    entity.setHashedUserId("some-user-id".getBytes());
    entity.setStatus(OPEN);
    return entity;
  }

  @Nested
  class BatchTable {
    @Test
    void saveAndLoadEntity() {
      final BatchEntity entity = createBatchEntity();

      final BatchEntity saved = batchRepository.save(entity);
      assertThat(saved.getBatchId()).isNotNull();

      final Optional<BatchEntity> fromDatabase = batchRepository.findById(saved.getBatchId());

      assertThat(fromDatabase).isPresent();
      final BatchEntity fromDbEntity = fromDatabase.get();
      assertThat(fromDbEntity)
          .usingRecursiveComparison()
          .ignoringFields("createdAt")
          .isEqualTo(saved);
      assertThat(fromDbEntity.getCreatedAt())
          .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
      assertThat(fromDbEntity.getStatus()).isEqualTo(OPEN);
    }
  }

  @Nested
  class BatchRepositoryTest {
    @Test
    void incrementNumberOfNotifications() {
      final int notificationCountInitial = 23;
      final int increment = 7;

      BatchEntity entity = createBatchEntity();
      entity.setNumberOfNotifications(notificationCountInitial);
      entity = batchRepository.save(entity);

      // create another entity, to assure that the where condition works
      batchRepository.save(createBatchEntity());

      final int rowsUpdated =
          batchRepository.incrementNumberOfNotifications(entity.getBatchId(), increment);

      assertThat(rowsUpdated).isEqualTo(1);

      final BatchEntity entityFromDatabase =
          batchRepository.findById(entity.getBatchId()).orElseThrow();
      assertThat(entityFromDatabase)
          .usingRecursiveComparison()
          .ignoringFields("createdAt", "numberOfNotifications")
          .isEqualTo(entity);
      assertThat(entityFromDatabase.getNumberOfNotifications())
          .isEqualTo(notificationCountInitial + increment);
    }

    @Test
    void updateStatus() {
      final BatchEntity entity = batchRepository.save(createBatchEntity());

      // create another entity, to assure that the where condition works
      batchRepository.save(createBatchEntity());

      final BatchStatus newStatus = BatchStatus.CLOSED;

      final int rowsUpdated = batchRepository.updateStatus(entity.getBatchId(), newStatus);

      assertThat(rowsUpdated).isEqualTo(1);

      final BatchEntity entityFromDatabase =
          batchRepository.findById(entity.getBatchId()).orElseThrow();
      assertThat(entityFromDatabase)
          .usingRecursiveComparison()
          .ignoringFields("createdAt", "status")
          .isEqualTo(entity);
      assertThat(entityFromDatabase.getStatus()).isEqualTo(newStatus);
    }
  }
}

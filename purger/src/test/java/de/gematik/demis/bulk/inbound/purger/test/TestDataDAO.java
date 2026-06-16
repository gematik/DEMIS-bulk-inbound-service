package de.gematik.demis.bulk.inbound.purger.test;

/*-
 * #%L
 * bulk-inbound-purger
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

import static de.gematik.demis.bulk.inbound.purger.test.TestWithPostgresContainer.DB_ADMIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class TestDataDAO {

  private final EntityManager em;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID insertBatch(final Instant createdAt, final int status) {
    // the purger-db-user is not permitted to insert, thus switch role (permitted in db-init.sql)
    switchToAdminRole();

    final UUID batchId = UUID.randomUUID();
    final String sql =
        "insert into batch (batch_id, created_at, hashed_user_id, status) values (:batchId, :createdAt, :hashedUserId, :status)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("batchId", batchId)
            .setParameter("createdAt", createdAt)
            .setParameter("hashedUserId", "does-not-matter".getBytes())
            .setParameter("status", status)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
    return batchId;
  }

  private void switchToAdminRole() {
    em.createNativeQuery("SET ROLE " + DB_ADMIN_USER).executeUpdate();
  }

  @Transactional(readOnly = true)
  public boolean batchExists(final UUID batchId) {
    final Number count =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM batch WHERE batch_id = :batchId")
                .setParameter("batchId", batchId)
                .getSingleResult();
    return count.longValue() > 0;
  }
}

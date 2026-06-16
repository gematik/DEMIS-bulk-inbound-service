package de.gematik.demis.bulk.inbound.service.repository;

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

import de.gematik.demis.bulk.inbound.service.entity.BatchEntity;
import de.gematik.demis.bulk.inbound.service.service.BatchStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BatchRepository extends CrudRepository<BatchEntity, UUID> {

  @Modifying
  @Transactional
  @Query(
      "UPDATE #{#entityName} e SET e.numberOfNotifications = e.numberOfNotifications + :delta WHERE e.batchId = :id")
  int incrementNumberOfNotifications(@Param("id") UUID id, @Param("delta") int delta);

  @Modifying
  @Transactional
  @Query("UPDATE #{#entityName} e SET e.status = :newStatus WHERE e.batchId = :id")
  int updateStatus(@Param("id") UUID id, @Param("newStatus") BatchStatus newStatus);
}

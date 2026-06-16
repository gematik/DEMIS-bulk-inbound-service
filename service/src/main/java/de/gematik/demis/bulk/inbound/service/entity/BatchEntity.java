package de.gematik.demis.bulk.inbound.service.entity;

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

import de.gematik.demis.bulk.inbound.service.service.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "batch")
@Setter
@Getter
@ToString
public class BatchEntity {

  @GeneratedValue(strategy = GenerationType.UUID)
  @Id
  @Column
  private UUID batchId;

  // hashed sender (token.preferred_username)
  @Column(insertable = true, updatable = false, nullable = false)
  private byte[] hashedUserId;

  // is set via db trigger
  @Column(insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  // attribute is not updated via entity, use repository operation instead
  @Column(insertable = true, updatable = false, nullable = false)
  private int numberOfNotifications;

  // attribute is not updated via entity, use repository operation instead
  @Column(insertable = true, updatable = false, nullable = false)
  private BatchStatus status;
}

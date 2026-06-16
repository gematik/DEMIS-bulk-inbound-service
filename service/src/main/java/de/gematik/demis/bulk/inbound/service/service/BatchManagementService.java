package de.gematik.demis.bulk.inbound.service.service;

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

import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.BATCH_ALREADY_CLOSED;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.BATCH_NOT_FOUND;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.BATCH_NOT_PERMITTED;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.DB_UPDATE_FAILED;
import static java.util.Objects.requireNonNull;

import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.entity.BatchEntity;
import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.bulk.inbound.service.messaging.messages.CloseBatchMessage;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.service.base.security.crypto.HashService;
import feign.Response;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service that manages requests related to batch lifecycle operations and batch permissions */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchManagementService {

  public static final String BATCH_NOT_FOUND_MSG = "Batch with id %s not found.";
  public static final String BATCH_ALREADY_CLOSED_MSG = "Batch with ID %s has already been closed.";
  private final BatchRepository batchRepository;
  private final HashService hashService;
  private final WafServiceClient client;

  /**
   * Creates a new {@code Batch}, marks it as open, persists it, and assigns it to the specified
   * user.
   *
   * @param sender required. preferred username from jwt token
   * @return generated new uuid
   */
  public UUID startBatch(final String sender) {
    BatchEntity batchEntity = new BatchEntity();
    batchEntity.setHashedUserId(hash(requireNonNull(sender)));
    batchEntity.setStatus(BatchStatus.OPEN);
    batchEntity = batchRepository.save(batchEntity);
    log.info("new batch {}", batchEntity);
    return batchEntity.getBatchId();
  }

  /**
   * Checks if the given batch is owned by the sender and not in status closed. If not a
   * ServiceException is thrown with code BATCH_NOT_FOUND or BATCH_NOT_PERMITTED
   *
   * @param sender required. preferred username from jwt token
   * @param batchId required id of the batch
   */
  public void checkPermission(final String sender, final UUID batchId) {
    final BatchEntity batchEntity = batchRepository.findById(requireNonNull(batchId)).orElse(null);
    if (batchEntity == null) {
      final String msg = "Batch " + batchId + " does not exist.";
      throw BATCH_NOT_FOUND.exception(msg);
    }
    if (!Arrays.equals(batchEntity.getHashedUserId(), hash(requireNonNull(sender)))) {
      final String msg = "Batch " + batchId + " is not permitted for " + sender;
      throw BATCH_NOT_PERMITTED.exception(msg);
    }
    if (batchEntity.getStatus() == BatchStatus.CLOSED) {
      throw BATCH_ALREADY_CLOSED.exception(String.format(BATCH_ALREADY_CLOSED_MSG, batchId));
    }
  }

  private byte[] hash(final String sender) {
    return hashService.hash(sender);
  }

  /**
   * Closes a batch by sending a status message to the control queue and deleting the batch from the
   * repository.
   *
   * @param batchId The identifier of the batch that has to be deleted.
   */
  public void closeBatch(final UUID batchId) {
    sendStatusMessageToWaf(batchId, determineNumberOfMessagesInBatch(batchId));
    markBatchAsClosed(batchId);
    log.info("batch {} is closed now", batchId);
  }

  private int determineNumberOfMessagesInBatch(final UUID batchId) {
    Optional<BatchEntity> batchEntity = batchRepository.findById(batchId);
    return batchEntity
        .map(BatchEntity::getNumberOfNotifications)
        .orElseThrow(() -> BATCH_NOT_FOUND.exception(String.format(BATCH_NOT_FOUND_MSG, batchId)));
  }

  private void markBatchAsClosed(final UUID batchId) {
    final int updatedRows = batchRepository.updateStatus(batchId, BatchStatus.CLOSED);
    if (updatedRows != 1) {
      throw DB_UPDATE_FAILED.exception("error updating batch status. Updated Rows: " + updatedRows);
    }
  }

  private void sendStatusMessageToWaf(final UUID batchId, final int numberOfMessages) {
    try (final Response resp =
        client.sendStatusToWaf(
            new CloseBatchMessage(numberOfMessages),
            batchId.toString(),
            UUID.randomUUID().toString())) {
      if (resp.status() != 202) {
        throw ErrorCode.INTERNAL_SERVER_ERROR.exception("Failed to close batch with id " + batchId);
      }
    }
  }
}

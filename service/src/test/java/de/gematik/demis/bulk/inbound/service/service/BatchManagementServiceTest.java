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

import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MOCK_REQUEST;
import static de.gematik.demis.bulk.inbound.service.service.BatchManagementService.BATCH_ALREADY_CLOSED_MSG;
import static de.gematik.demis.bulk.inbound.service.service.BatchManagementService.BATCH_NOT_FOUND_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.entity.BatchEntity;
import de.gematik.demis.bulk.inbound.service.messaging.messages.CloseBatchMessage;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.security.crypto.HashService;
import feign.Response;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchManagementServiceTest {

  private static final String SENDER = "tester";
  private static final byte[] HASH = "a1xc2".getBytes();
  private static final int NUMBER_OF_MESSAGES_IN_BATCH = 4711;

  @Mock private BatchRepository batchRepository;
  @Mock private HashService hashService;
  @Mock private WafServiceClient client;

  @InjectMocks private BatchManagementService underTest;

  @Captor private ArgumentCaptor<BatchEntity> batchSaveCaptor;
  @Captor private ArgumentCaptor<UUID> batchIdCaptor;
  @Captor private ArgumentCaptor<BatchStatus> batchStatusCaptor;
  @Captor private ArgumentCaptor<CloseBatchMessage> closeBatchMessageArgumentCaptor;
  @Captor private ArgumentCaptor<String> messageIdCaptor;

  @Test
  void startBatch() {
    final UUID generatedBatchId = UUID.randomUUID();
    mockHashCall();
    mockBatchEntitySaveCall(generatedBatchId);

    final UUID result = underTest.startBatch(SENDER);

    verify(batchRepository).save(batchSaveCaptor.capture());
    assertThat(batchSaveCaptor.getValue())
        .returns(HASH, BatchEntity::getHashedUserId)
        .returns(null, BatchEntity::getBatchId)
        .returns(null, BatchEntity::getCreatedAt)
        .returns(BatchStatus.OPEN, BatchEntity::getStatus);
    assertThat(result).isEqualTo(generatedBatchId);
  }

  @Test
  void checkPermission_Okay() {
    final UUID batchId = UUID.randomUUID();
    final BatchEntity batchEntity = new BatchEntity();
    batchEntity.setBatchId(batchId);
    batchEntity.setHashedUserId(HASH);

    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    mockHashCall();

    assertDoesNotThrow(() -> underTest.checkPermission(SENDER, batchId));
  }

  @Test
  void checkPermission_BatchNotFound() {
    final UUID batchId = UUID.randomUUID();
    when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> underTest.checkPermission(SENDER, batchId))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertThat(ex)
                    .extracting(
                        ServiceException::getErrorCode,
                        ServiceException::getResponseStatus,
                        ServiceException::getMessage)
                    .containsExactly(
                        "BATCH_NOT_FOUND", FORBIDDEN, "Batch " + batchId + " does not exist."));
  }

  @Test
  void checkPermission_BatchNotPermitted() {
    final UUID batchId = UUID.randomUUID();
    final BatchEntity batchEntity = new BatchEntity();
    batchEntity.setBatchId(batchId);
    batchEntity.setHashedUserId("some-other-user".getBytes());

    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    mockHashCall();

    assertThatThrownBy(() -> underTest.checkPermission(SENDER, batchId))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertThat(ex)
                    .extracting(
                        ServiceException::getErrorCode,
                        ServiceException::getResponseStatus,
                        ServiceException::getMessage)
                    .containsExactly(
                        "BATCH_NOT_PERMITTED",
                        FORBIDDEN,
                        "Batch " + batchId + " is not permitted for " + SENDER));
  }

  @Test
  void checkPermission_BatchAlreadyClosed() {
    final UUID batchId = UUID.randomUUID();
    final BatchEntity batchEntity = new BatchEntity();
    batchEntity.setBatchId(batchId);
    batchEntity.setHashedUserId(HASH);
    batchEntity.setStatus(BatchStatus.CLOSED);

    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    mockHashCall();

    assertThatThrownBy(() -> underTest.checkPermission(SENDER, batchId))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertThat(ex)
                    .extracting(
                        ServiceException::getErrorCode,
                        ServiceException::getResponseStatus,
                        ServiceException::getMessage)
                    .containsExactly(
                        "BATCH_ALREADY_CLOSED",
                        FORBIDDEN,
                        String.format(BATCH_ALREADY_CLOSED_MSG, batchId)));
  }

  private void mockHashCall() {
    when(hashService.hash(SENDER)).thenReturn(HASH);
  }

  private void mockBatchEntitySaveCall(final UUID generatedUUID) {
    when(batchRepository.save(any()))
        .thenAnswer(
            invocation -> {
              final BatchEntity original = invocation.getArgument(0);
              final BatchEntity copy = new BatchEntity();
              copy.setHashedUserId(original.getHashedUserId());
              copy.setBatchId(generatedUUID);
              return copy;
            });
  }

  @Test
  @SneakyThrows
  void closeBatch() {
    final UUID batchId = UUID.fromString(BATCH_ID);

    final BatchEntity batchEntity = new BatchEntity();
    batchEntity.setBatchId(batchId);
    batchEntity.setNumberOfNotifications(NUMBER_OF_MESSAGES_IN_BATCH);
    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    when(batchRepository.updateStatus(any(), any())).thenReturn(1);
    when(client.sendStatusToWaf(any(), any(), any()))
        .thenReturn(Response.builder().status(202).request(MOCK_REQUEST).build());

    underTest.closeBatch(batchId);
    verify(client)
        .sendStatusToWaf(
            closeBatchMessageArgumentCaptor.capture(),
            eq(batchId.toString()),
            messageIdCaptor.capture());

    assertThat(closeBatchMessageArgumentCaptor.getValue())
        .returns(NUMBER_OF_MESSAGES_IN_BATCH, CloseBatchMessage::numberOfMessages);
    assertThat(messageIdCaptor.getValue()).isNotNull();
    assertDoesNotThrow(() -> UUID.fromString(messageIdCaptor.getValue()));

    verify(batchRepository).updateStatus(batchIdCaptor.capture(), batchStatusCaptor.capture());
    assertThat(batchIdCaptor.getValue()).isEqualTo(batchId);
    assertThat(batchStatusCaptor.getValue()).isEqualTo(BatchStatus.CLOSED);
    verifyNoMoreInteractions(batchRepository);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionSendingCloseToWafFails() {
    final UUID batchId = UUID.fromString(BATCH_ID);

    BatchEntity batchEntity = new BatchEntity();
    batchEntity.setNumberOfNotifications(NUMBER_OF_MESSAGES_IN_BATCH);
    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    when(client.sendStatusToWaf(any(), any(), any()))
        .thenReturn(Response.builder().status(400).request(MOCK_REQUEST).build());

    assertThatThrownBy(() -> underTest.closeBatch(batchId))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("Failed to close batch with id " + batchId);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionWhenBatchIsNotFoundInDatabase() {
    final UUID batchId = UUID.randomUUID();

    when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> underTest.closeBatch(batchId))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining(String.format(BATCH_NOT_FOUND_MSG, batchId));
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionWhenBatchCannotBeClosed() {
    final UUID batchId = UUID.fromString(BATCH_ID);

    final BatchEntity batchEntity = new BatchEntity();
    batchEntity.setBatchId(batchId);
    batchEntity.setNumberOfNotifications(NUMBER_OF_MESSAGES_IN_BATCH);
    when(batchRepository.findById(batchId)).thenReturn(Optional.of(batchEntity));
    when(batchRepository.updateStatus(any(), any())).thenReturn(0);
    when(client.sendStatusToWaf(any(), any(), any()))
        .thenReturn(Response.builder().status(202).request(MOCK_REQUEST).build());

    assertThatThrownBy(() -> underTest.closeBatch(batchId))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("error updating batch status");
  }
}

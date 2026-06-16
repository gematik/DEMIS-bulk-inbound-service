package de.gematik.demis.bulk.inbound.service.service.batchupload;

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
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationDataDump;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationDataDumpReader;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationDataDumpReaderFromNotifications;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationIdsDump;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.EXCHANGE;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.ROUTING_KEY_IN;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService.TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG;
import static de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService.TOO_FEW_DOC_IDS_ERROR_MSG;
import static de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService.TOO_MANY_DOCUMENT_IDS_ERROR_MSG;
import static de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService.VERIFY_ERROR_MSG;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.PROCESSING;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.TOOLONG;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.bulk.inbound.service.config.RabbitConfig;
import de.gematik.demis.bulk.inbound.service.exception.BulkInboundServiceValidationException;
import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.bulk.inbound.service.service.RequestHeadersAccessor;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import java.io.BufferedReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UploadBatchServiceTest {

  private static final String FAKE_BEARER_TOKEN = "Bearer authData";
  private static final String AES_SECRET = "0123456789abcdef";

  private AESEncryptionService encryptionService;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private RequestHeadersAccessor requestHeadersAccessor;
  @Mock private BatchRepository batchRepository;
  @Captor private ArgumentCaptor<Message> messageCaptor;
  @Captor private ArgumentCaptor<CorrelationData> correlationDataArgumentCaptor;
  @Captor private ArgumentCaptor<Integer> updateCountArgumentCaptor;

  private UploadBatchService underTest;

  @BeforeEach
  @SneakyThrows
  void setUp() {
    encryptionService = Mockito.spy(new AESEncryptionService(AES_SECRET.getBytes()));
    underTest =
        new UploadBatchService(
            encryptionService, rabbitTemplate, requestHeadersAccessor, batchRepository);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfDocumentIdsEmpty() {
    BufferedReader reader = generateNotificationDataDumpReader(1);
    final ServiceException ex =
        assertThrows(
            ServiceException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, "", reader));
    assertThat(ex.getMessage()).isEqualTo("No document IDs were provided in the request header.");
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfDocumentIdDuplicated() {
    BufferedReader reader = generateNotificationDataDumpReader(1);
    final ServiceException ex =
        assertThrows(
            ServiceException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, "1,1", reader));
    assertThat(ex.getMessage())
        .isEqualTo("Duplicate document IDs were provided in the request header.");
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5})
  void shouldProcessBatchSuccessfully(int amount) {
    final List<String> notifications = generateNotificationDataDump(amount);
    CorrelationData.Confirm confirm = mock(CorrelationData.Confirm.class);
    when(confirm.ack()).thenReturn(true);

    when(batchRepository.incrementNumberOfNotifications(
            eq(UUID.fromString(BATCH_ID)), updateCountArgumentCaptor.capture()))
        .thenReturn(1);

    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              correlationData.getFuture().complete(confirm);
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    underTest.processBatch(
        BATCH_ID,
        FAKE_BEARER_TOKEN,
        generateNotificationIdsDump(amount),
        generateNotificationDataDumpReaderFromNotifications(notifications));
    verify(encryptionService, times(amount)).encryptData(FAKE_BEARER_TOKEN);

    verify(encryptionService, times(amount)).encryptData(FAKE_BEARER_TOKEN);
    verify(encryptionService, times(amount * 2)).encryptData(anyString());

    verify(rabbitTemplate, times(amount))
        .send(
            eq(RabbitConfig.EXCHANGE),
            eq(ROUTING_KEY_IN),
            messageCaptor.capture(),
            correlationDataArgumentCaptor.capture());
    final List<Message> messages = messageCaptor.getAllValues();
    assertThat(messages).hasSize(amount);
    for (int i = 0; i < messageCaptor.getAllValues().size(); i++) {
      assertThat(encryptionService.decryptData(messages.get(i).getBody()))
          .isEqualTo(notifications.get(i));
      Map<String, Object> messageHeaders = messages.get(i).getMessageProperties().getHeaders();
      assertThat(messageHeaders)
          .isNotNull()
          .containsEntry(HEADER_DOCUMENT_ID, String.valueOf(i))
          .containsEntry(HEADER_BATCH_ID, BATCH_ID)
          .containsKey(HEADER_MESSAGE_ID);
      assertDoesNotThrow(() -> UUID.fromString(messageHeaders.get(HEADER_MESSAGE_ID).toString()));

      assertThat(updateCountArgumentCaptor.getValue()).isEqualTo(amount);
    }
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfToMuchIds() {
    CorrelationData.Confirm confirm = mock(CorrelationData.Confirm.class);
    when(confirm.ack()).thenReturn(true);
    String documentIds = generateNotificationIdsDump(3);
    BufferedReader reader = generateNotificationDataDumpReader(1);

    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              correlationData.getFuture().complete(confirm);
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final OperationOutcome expected = new OperationOutcome();
    expected
        .addIssue()
        .setSeverity(ERROR)
        .setCode(TOOLONG)
        .setDiagnostics(TOO_MANY_DOCUMENT_IDS_ERROR_MSG + "1");
    expected
        .addIssue()
        .setSeverity(ERROR)
        .setCode(TOOLONG)
        .setDiagnostics(TOO_MANY_DOCUMENT_IDS_ERROR_MSG + "2");

    final BulkInboundServiceValidationException ex =
        assertThrows(
            BulkInboundServiceValidationException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, documentIds, reader));
    assertThat(ex.getMessage()).isEqualTo(VERIFY_ERROR_MSG);
    assertThat(ex.getOperationOutcome()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(ex.getErrorCode())
        .isEqualTo(ErrorCode.INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION.getCode());
    assertThat(ex.getResponseStatus())
        .isEqualTo(ErrorCode.INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION.getHttpStatus());
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfAccFalse() {
    CorrelationData.Confirm confirm = mock(CorrelationData.Confirm.class);
    when(confirm.ack()).thenReturn(false);
    String documentIds = generateNotificationIdsDump(1);
    BufferedReader reader = generateNotificationDataDumpReader(1);

    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              correlationData.getFuture().complete(confirm);
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final OperationOutcome expected = new OperationOutcome();
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "0");

    final BulkInboundServiceValidationException ex =
        assertThrows(
            BulkInboundServiceValidationException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, documentIds, reader));
    assertThat(ex.getMessage()).isEqualTo(VERIFY_ERROR_MSG);
    assertThat(ex.getOperationOutcome()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RABBIT_MQ_ERROR.getCode());
    assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfConfirmEqualsNull() {
    String documentIds = generateNotificationIdsDump(1);
    BufferedReader reader = generateNotificationDataDumpReader(1);
    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              correlationData.getFuture().complete(null);
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final OperationOutcome expected = new OperationOutcome();
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "0");

    final BulkInboundServiceValidationException ex =
        assertThrows(
            BulkInboundServiceValidationException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, documentIds, reader));
    assertThat(ex.getMessage()).isEqualTo(VERIFY_ERROR_MSG);
    assertThat(ex.getOperationOutcome()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RABBIT_MQ_ERROR.getCode());
    assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfConfirmEqualsNullAndAckFalse() {
    String documentIds = generateNotificationIdsDump(2);
    BufferedReader reader = generateNotificationDataDumpReader(2);
    CorrelationData.Confirm confirm = mock(CorrelationData.Confirm.class);
    when(confirm.ack()).thenReturn(false);
    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              Message message = invocation.getArgument(2);
              if (message
                  .getMessageProperties()
                  .getHeader(HEADER_DOCUMENT_ID)
                  .equals(documentIds.split(",")[0])) {
                correlationData.getFuture().complete(null);
              } else {
                correlationData.getFuture().complete(confirm);
              }
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final OperationOutcome expected = new OperationOutcome();
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "0");
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "1");

    final BulkInboundServiceValidationException ex =
        assertThrows(
            BulkInboundServiceValidationException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, documentIds, reader));
    assertThat(ex.getMessage()).isEqualTo(VERIFY_ERROR_MSG);
    assertThat(ex.getOperationOutcome()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RABBIT_MQ_ERROR.getCode());
    assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @SneakyThrows
  void shouldThrowExceptionIfConfirmEqualsNullAndAckFalseAndToMuchIds() {
    String documentIds = generateNotificationIdsDump(4);
    BufferedReader reader = generateNotificationDataDumpReader(2);
    CorrelationData.Confirm confirmNeg = mock(CorrelationData.Confirm.class);
    when(confirmNeg.ack()).thenReturn(false);
    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              Message message = invocation.getArgument(2);
              if (message
                  .getMessageProperties()
                  .getHeader(HEADER_DOCUMENT_ID)
                  .equals(documentIds.split(",")[0])) {
                correlationData.getFuture().complete(null);
              } else if (message
                  .getMessageProperties()
                  .getHeader(HEADER_DOCUMENT_ID)
                  .equals(documentIds.split(",")[1])) {
                correlationData.getFuture().complete(confirmNeg);
              }
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final OperationOutcome expected = new OperationOutcome();
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "0");
    expected
        .addIssue()
        .setSeverity(FATAL)
        .setCode(PROCESSING)
        .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + "1");
    expected
        .addIssue()
        .setSeverity(ERROR)
        .setCode(TOOLONG)
        .setDiagnostics(TOO_MANY_DOCUMENT_IDS_ERROR_MSG + "2");
    expected
        .addIssue()
        .setSeverity(ERROR)
        .setCode(TOOLONG)
        .setDiagnostics(TOO_MANY_DOCUMENT_IDS_ERROR_MSG + "3");

    final BulkInboundServiceValidationException ex =
        assertThrows(
            BulkInboundServiceValidationException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, documentIds, reader));
    assertThat(ex.getMessage()).isEqualTo(VERIFY_ERROR_MSG);
    assertThat(ex.getOperationOutcome()).usingRecursiveComparison().isEqualTo(expected);
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RABBIT_MQ_ERROR.getCode());
    assertThat(ex.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ParameterizedTest
  @SneakyThrows
  @CsvSource({"1,2", "1,5"})
  void shouldThrowExceptionIfNotificationAmountNotMatchDocumentIds(int value1, int value2) {
    String ids = generateNotificationIdsDump(value1);
    BufferedReader notifications = generateNotificationDataDumpReader(value2);
    ServiceException exception =
        assertThrows(
            ServiceException.class,
            () -> underTest.processBatch(BATCH_ID, FAKE_BEARER_TOKEN, ids, notifications));
    assertThat(exception).isInstanceOf(ServiceException.class);
    assertThat(exception.getMessage()).isEqualTo(format(TOO_FEW_DOC_IDS_ERROR_MSG, value1));
  }

  @SneakyThrows
  @Test
  void shouldThrowExceptionWhenBatchUpdateFailed() {
    final List<String> notifications = generateNotificationDataDump(1);
    CorrelationData.Confirm confirm = mock(CorrelationData.Confirm.class);
    when(confirm.ack()).thenReturn(true);

    doAnswer(
            invocation -> {
              CorrelationData correlationData = invocation.getArgument(3);
              correlationData.getFuture().complete(confirm);
              return null;
            })
        .when(rabbitTemplate)
        .send(eq(EXCHANGE), eq(ROUTING_KEY_IN), any(Message.class), any(CorrelationData.class));

    final String notificationIds = generateNotificationIdsDump(1);
    final BufferedReader notificationData =
        generateNotificationDataDumpReaderFromNotifications(notifications);

    final ServiceException exception =
        assertThrows(
            ServiceException.class,
            () ->
                underTest.processBatch(
                    BATCH_ID, FAKE_BEARER_TOKEN, notificationIds, notificationData));

    assertThat(exception).isInstanceOf(ServiceException.class);
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DB_UPDATE_FAILED.getCode());
    assertThat(exception.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(exception.getMessage())
        .isEqualTo("error updating notification count. Updated rows: 0");
  }
}

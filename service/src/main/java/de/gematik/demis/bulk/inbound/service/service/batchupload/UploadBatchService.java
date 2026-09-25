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

import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.EXCHANGE;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.ROUTING_KEY_IN;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.DB_UPDATE_FAILED;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION;
import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.RABBIT_MQ_ERROR;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static java.lang.String.format;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;

import de.gematik.demis.bulk.inbound.service.exception.BulkInboundServiceValidationException;
import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.bulk.inbound.service.service.RequestHeadersAccessor;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Handles processing logic for incoming batch bundles. */
@Service
@AllArgsConstructor
@Slf4j
public class UploadBatchService {

  public static final String TOO_FEW_DOC_IDS_ERROR_MSG =
      "Sent document IDs are less than notifications in the batch. %s Notifications got transmitted.";
  public static final String VERIFY_ERROR_MSG =
      "Errors occurred during processing the batch upload.";
  public static final String TOO_MANY_DOCUMENT_IDS_ERROR_MSG =
      "Could not order any notification to ID: ";
  public static final String TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG =
      "Failed to process document ID's: ";

  private final AESEncryptionService encryptionService;
  private final RabbitTemplate rabbitTemplate;
  private final RequestHeadersAccessor requestHeadersAccessor;
  private final BatchRepository batchRepository;

  /**
   * Processes a single batch request.
   *
   * @param batchId The ID of the batch.
   * @param authorization The access token that was sent with the HTTP request.
   * @param documentIds The document IDs that were sent with the HTTP request.
   * @param reader BufferedReader to stream notifications line by line.
   */
  public void processBatch(
      final String batchId,
      final String authorization,
      final String documentIds,
      final BufferedReader reader) {
    final List<String> documentIdsList =
        List.of(StringUtils.delimitedListToStringArray(documentIds, ","));
    checkDocumentId(documentIdsList);
    sendNotificationsToRabbitMq(batchId, authorization, reader, documentIdsList);
    updateNumberOfNotifications(UUID.fromString(batchId), documentIdsList.size());
  }

  private static void checkDocumentId(final List<String> documentIdsList) {
    if (documentIdsList.isEmpty()) {
      throw new ServiceException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.MISSING_DOCUMENT_IDS.getCode(),
          "No document IDs were provided in the request header.");
    }
    if (documentIdsList.stream().anyMatch(String::isBlank)) {
      throw new ServiceException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.EMPTY_DOCUMENT_ID.getCode(),
          "Every DocumentId within the header's list string must not be empty.");
    }
    if (documentIdsList.size() != documentIdsList.stream().distinct().count()) {
      throw new ServiceException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.DUPLICATE_DOCUMENT_IDS.getCode(),
          "Duplicate document IDs were provided in the request header.");
    }
  }

  private void sendNotificationsToRabbitMq(
      final String batchId,
      final String authorization,
      final BufferedReader reader,
      final List<String> documentIdsList) {
    int count = 0;
    final List<CorrelationData> correlationDataList = new ArrayList<>();

    try {
      String notification;
      while ((notification = reader.readLine()) != null) {
        if (notification.isBlank()) {
          continue;
        }
        checkIdPresentForNotification(documentIdsList, count);
        final String documentId = documentIdsList.get(count).trim();
        ++count;
        final byte[] encryptedMessage = encryptionService.encryptData(notification);
        final byte[] encryptedAuthorization = encryptionService.encryptData(authorization);
        final Message message =
            buildMessage(batchId, encryptedMessage, documentId, encryptedAuthorization);

        final CorrelationData correlationData = new CorrelationData(documentId);
        correlationDataList.add(correlationData);
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY_IN, message, correlationData);
      }
      verifyExecutions(documentIdsList, correlationDataList);
    } catch (IOException exception) {
      throw new ServiceException(
          ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus(),
          ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
          "Internal server error during reading the batch payload.",
          exception);
    }
  }

  private void verifyExecutions(
      final List<String> documentIdsList, final List<CorrelationData> correlationDataList) {
    final List<String> correlationDataErrors = validateCorrelationData(correlationDataList);
    if (!correlationDataErrors.isEmpty() || correlationDataList.size() != documentIdsList.size()) {
      OperationOutcome outcome = new OperationOutcome();
      ErrorCode errorCode = INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION;
      for (String documentId : correlationDataErrors) {
        errorCode = RABBIT_MQ_ERROR;
        outcome
            .addIssue()
            .setSeverity(FATAL)
            .setCode(IssueType.PROCESSING)
            .setDiagnostics(TECHNICAL_ISSUE_RABBITMQ_ERROR_MSG + documentId);
      }
      for (String documentId :
          documentIdsList.subList(correlationDataList.size(), documentIdsList.size())) {
        outcome
            .addIssue()
            .setSeverity(ERROR)
            .setCode(IssueType.TOOLONG)
            .setDiagnostics(TOO_MANY_DOCUMENT_IDS_ERROR_MSG + documentId);
      }
      throw new BulkInboundServiceValidationException(errorCode, VERIFY_ERROR_MSG, outcome);
    }
  }

  private List<String> validateCorrelationData(final List<CorrelationData> correlationDataList) {
    List<String> errors = new ArrayList<>();
    for (CorrelationData correlationData : correlationDataList) {
      validateCorrelationData(correlationData, errors);
    }
    return errors;
  }

  private static void validateCorrelationData(
      final CorrelationData correlationData, final List<String> errors) {
    CorrelationData.Confirm confirm;
    try {
      confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
      if (confirm == null || !confirm.ack()) {
        errors.add(correlationData.getId());
      }
    } catch (ExecutionException | TimeoutException exception) {
      errors.add(correlationData.getId());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServiceException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
          "Thread got terminated",
          e);
    }
  }

  private Message buildMessage(
      final String batchId,
      final byte[] encryptedMessage,
      final String documentId,
      final byte[] authorization) {
    return MessageBuilder.withBody(encryptedMessage)
        .andProperties(
            MessagePropertiesBuilder.newInstance()
                .setHeader(HEADER_MESSAGE_ID, UUID.randomUUID().toString())
                .setHeader(HEADER_BATCH_ID, batchId)
                .setHeader(HEADER_DOCUMENT_ID, documentId)
                .setHeader(HEADER_AUTHORIZATION, authorization)
                .setHeader(
                    HEADER_FHIR_PACKAGE_VERSION,
                    requestHeadersAccessor.determineHeaderXFhirPackageVersion())
                .setHeader(
                    HEADER_FHIR_PACKAGE, requestHeadersAccessor.determineHeaderXFhirPackage())
                .build())
        .build();
  }

  private static void checkIdPresentForNotification(
      final List<String> documentIdsList, final int count) {
    if (documentIdsList.size() - 1 < count) {
      throw new ServiceException(
          HttpStatus.BAD_REQUEST,
          INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION.getCode(),
          format(TOO_FEW_DOC_IDS_ERROR_MSG, count));
    }
  }

  private void updateNumberOfNotifications(final UUID batchId, final int numberOfNewNotifications) {
    final int updatedRows =
        batchRepository.incrementNumberOfNotifications(batchId, numberOfNewNotifications);
    if (updatedRows != 1) {
      // should never happen
      throw DB_UPDATE_FAILED.exception(
          "error updating notification count. Updated rows: " + updatedRows);
    }
  }
}

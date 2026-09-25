package de.gematik.demis.bulk.inbound.service.messaging;

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

import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.IN_QUEUE;
import static de.gematik.demis.bulk.inbound.service.messaging.ErrorType.PROCESSING_ERROR;
import static de.gematik.demis.bulk.inbound.service.messaging.ErrorType.WAF;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;

import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.exception.RetryableException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Handles messages received from the IN queue. */
@Component
@Slf4j
@RequiredArgsConstructor
public class InQueueMessageHandler {

  private final WafServiceClient wafServiceClient;
  private final AESEncryptionService encryptionService;

  /**
   * Processes a message received from the IN queue checks it against WAF.
   *
   * @param message The message received from the IN queue.
   */
  @RabbitListener(queues = IN_QUEUE)
  public void processInQueueMessage(final Message message) {
    // first read all required properties
    final MessageProperties messageProperties = message.getMessageProperties();
    final String messageId = getRequiredStringHeader(messageProperties, HEADER_MESSAGE_ID);
    final String batchId = getRequiredStringHeader(messageProperties, HEADER_BATCH_ID);
    final String documentId = getRequiredStringHeader(messageProperties, HEADER_DOCUMENT_ID);
    final String fhirPackageVersion =
        getRequiredStringHeader(messageProperties, HEADER_FHIR_PACKAGE_VERSION);
    final String fhirPackage = getRequiredStringHeader(messageProperties, HEADER_FHIR_PACKAGE);
    final byte[] encryptedAuthorization = messageProperties.getHeader(HEADER_AUTHORIZATION);

    final String authorization;
    final String msg;
    try {
      authorization = encryptionService.decryptData(encryptedAuthorization);
      msg = encryptionService.decryptData(message.getBody());
    } catch (final RuntimeException ex) {
      // most likely error reason: encryption key has rotated and old key is wrong.
      // we do not retry this error. instead we send an error message that leads to internal error
      // with high probability many notifications are affected
      log.error(
          "BatchId={}, DocumentId={} -> error while decryption. notification cannot be processed. Sending error message.",
          batchId,
          documentId,
          ex);
      sendErrorMessage(PROCESSING_ERROR, batchId, documentId, messageId);
      // message ack
      return;
    }

    final int status;
    try (final Response resp =
        wafServiceClient.sendNotificationToWaf(
            msg, messageId, batchId, documentId, authorization, fhirPackageVersion, fhirPackage)) {
      status = resp.status();
    } catch (final RuntimeException ex) {
      // for example: connection error (waf is down) or socket timeout (waf is too slow)
      // we retry this infinitive
      // in case of timeout we could produce here duplicates
      log.error(
          "BatchId={}, DocumentId={}. Retryable error: sending notification to waf failed. Cause={}",
          batchId,
          documentId,
          ex.getMessage());
      throw new RetryableException();
    }

    if (isFailed(status)) {
      if (isWafError(status)) {
        log.info(
            "BatchId={}, DocumentId={} -> message was rejected by the WAF. Response={}",
            batchId,
            documentId,
            status);
        sendErrorMessage(WAF, batchId, documentId, messageId);
      } else {
        log.error(
            "BatchId={}, DocumentId={}. Retryable error: sending notification to waf failed. Response={}",
            batchId,
            documentId,
            status);
        throw new RetryableException();
      }
    }
  }

  private String getRequiredStringHeader(
      final MessageProperties messageProperties, final String headerKey) {
    final Object value = messageProperties.getHeader(headerKey);
    if (value == null) {
      throw new IllegalStateException("Required header " + headerKey + " is missing");
    }
    final String s = value.toString();
    if (s.isBlank()) {
      throw new IllegalStateException("Required header " + headerKey + " is blank");
    }
    return s;
  }

  private void sendErrorMessage(
      final ErrorType type, final String batchId, final String documentId, final String messageId) {
    final String messagePayload = "{\"error\":\"" + type.name() + "\"}";
    final int status;
    try (final Response errorResp =
        wafServiceClient.sendErrorToWaf(messagePayload, batchId, documentId, messageId)) {
      status = errorResp.status();
    } catch (final RuntimeException ex) {
      log.error(
          "BatchId={}, DocumentId={} -> Retryable error: sending error message failed. Cause={}",
          batchId,
          documentId,
          ex.getMessage());
      throw new RetryableException(ex);
    }

    if (isFailed(status)) {
      final boolean retryable = status != 400;
      log.error(
          "BatchId={}, DocumentId={} -> Retryable error= {}. sending error message failed. HttpResponseStatus={}",
          batchId,
          documentId,
          retryable,
          status);
      throw retryable ? new RetryableException() : new IllegalArgumentException("bad request");
    }
  }

  private boolean isFailed(final int statusCode) {
    return statusCode < 200 || statusCode > 299;
  }

  private boolean isWafError(final int statusCode) {
    return statusCode >= 400 && statusCode < 500;
  }
}

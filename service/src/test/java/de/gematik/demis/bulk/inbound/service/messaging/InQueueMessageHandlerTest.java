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

import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MESSAGE_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MOCK_REQUEST;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.exception.RetryableException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePropertiesBuilder;

@ExtendWith(MockitoExtension.class)
class InQueueMessageHandlerTest {

  private static final String PAYLOAD = "my test notification message";

  private static final String AES_SECRET = "0123456789abcdef";

  @Mock WafServiceClient wafServiceClient;
  InQueueMessageHandler underTest;
  private AESEncryptionService encryptionService;

  @BeforeEach
  void init() {
    encryptionService = spy(new AESEncryptionService(AES_SECRET.getBytes()));
    underTest = new InQueueMessageHandler(wafServiceClient, encryptionService);
  }

  @Test
  void happyPath_should_callWafClientCorrectly() {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(202));

    underTest.processInQueueMessage(validMessage());

    verify(wafServiceClient)
        .sendNotificationToWaf(
            PAYLOAD,
            MESSAGE_ID,
            BATCH_ID,
            DOCUMENT_ID,
            AUTHORIZATION,
            FHIR_PACKAGE_VERSION,
            PACKAGE);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 415})
  void sendNotificationToWaf_wafError_should_callWafClientWithErrorMessage(int wafResponseStatus) {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(wafResponseStatus));
    when(wafServiceClient.sendErrorToWaf(any(), any(), any(), any())).thenReturn(response(202));

    underTest.processInQueueMessage(validMessage());

    verify(wafServiceClient)
        .sendErrorToWaf("{\"error\":\"WAF\"}", BATCH_ID, DOCUMENT_ID, MESSAGE_ID);
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 500})
  void sendNotificationToWaf_wafError_errorMessage_ErrorResponse_should_throwRetryableException(
      final int errorStatus) {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(403));
    when(wafServiceClient.sendErrorToWaf(any(), any(), any(), any()))
        .thenReturn(response(errorStatus));

    final Message message = validMessage();
    assertThatThrownBy(() -> underTest.processInQueueMessage(message))
        .isInstanceOf(RetryableException.class);
  }

  @Test
  void
      sendNotificationToWaf_wafError_errorMessage_400Response_should_throwException_ButNotRetryable() {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(403));
    when(wafServiceClient.sendErrorToWaf(any(), any(), any(), any())).thenReturn(response(400));

    final Message message = validMessage();
    assertThatThrownBy(() -> underTest.processInQueueMessage(message))
        .isNotInstanceOf(RetryableException.class);
  }

  @Test
  void
      sendNotificationToWaf_wafError_errorMessage_ExceptionInCall_should_throwRetryableException() {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(403));
    when(wafServiceClient.sendErrorToWaf(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("connection timeout"));

    final Message message = validMessage();
    assertThatThrownBy(() -> underTest.processInQueueMessage(message))
        .isInstanceOf(RetryableException.class);
  }

  @Test
  void sendNotificationToWaf_500Response_should_throwRetryableException() {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(response(500));

    final Message message = validMessage();
    assertThatThrownBy(() -> underTest.processInQueueMessage(message))
        .isInstanceOf(RetryableException.class);
  }

  @Test
  void sendNotificationToWaf_ExceptionInCall_should_throwRetryableException() {
    when(wafServiceClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("connection timeout"));

    final Message message = validMessage();
    assertThatThrownBy(() -> underTest.processInQueueMessage(message))
        .isInstanceOf(RetryableException.class);
  }

  @Test
  void missingRequiredHeader_should_throwException_ButNotRetryable() {
    final Message invalidMessage = validMessage();
    invalidMessage.getMessageProperties().getHeaders().remove(HEADER_FHIR_PACKAGE);

    assertThatThrownBy(() -> underTest.processInQueueMessage(invalidMessage))
        .isNotInstanceOf(RetryableException.class)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HEADER_FHIR_PACKAGE)
        .hasMessageContaining("missing");

    verifyNoInteractions(wafServiceClient);
  }

  @Test
  void emptyRequiredHeader_should_throwException_ButNotRetryable() {
    final Message invalidMessage = validMessage();
    invalidMessage.getMessageProperties().getHeaders().replace(HEADER_DOCUMENT_ID, "");

    assertThatThrownBy(() -> underTest.processInQueueMessage(invalidMessage))
        .isNotInstanceOf(RetryableException.class)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HEADER_DOCUMENT_ID)
        .hasMessageContaining("blank");

    verifyNoInteractions(wafServiceClient);
  }

  @Test
  void decryptionError_should_sendErrorMessage_noException() {
    when(wafServiceClient.sendErrorToWaf(any(), any(), any(), any())).thenReturn(response(202));

    final AESEncryptionService encryptionServiceWithWrongKey =
        new AESEncryptionService("other_secret_123".getBytes());
    final Message message =
        buildMessage(
            encryptionServiceWithWrongKey.encryptData(PAYLOAD),
            encryptionServiceWithWrongKey.encryptData(AUTHORIZATION));

    underTest.processInQueueMessage(message);

    verify(wafServiceClient)
        .sendErrorToWaf("{\"error\":\"PROCESSING_ERROR\"}", BATCH_ID, DOCUMENT_ID, MESSAGE_ID);
    verifyNoMoreInteractions(wafServiceClient);
  }

  private Response response(final int status) {
    return Response.builder().status(status).request(MOCK_REQUEST).build();
  }

  private Message validMessage() {
    return buildMessage(
        encryptionService.encryptData(PAYLOAD), encryptionService.encryptData(AUTHORIZATION));
  }

  private Message buildMessage(final byte[] payloadEncrypted, final byte[] authorizationEncrypted) {
    return MessageBuilder.withBody(payloadEncrypted)
        .andProperties(
            MessagePropertiesBuilder.newInstance()
                .setHeader(HEADER_MESSAGE_ID, MESSAGE_ID)
                .setHeader(HEADER_BATCH_ID, BATCH_ID)
                .setHeader(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .setHeader(HEADER_AUTHORIZATION, authorizationEncrypted)
                .setHeader(HEADER_FHIR_PACKAGE_VERSION, FHIR_PACKAGE_VERSION)
                .setHeader(HEADER_FHIR_PACKAGE, PACKAGE)
                .build())
        .build();
  }
}

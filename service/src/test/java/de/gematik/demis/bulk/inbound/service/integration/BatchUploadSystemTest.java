package de.gematik.demis.bulk.inbound.service.integration;

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
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MOCK_REQUEST;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.PACKAGE;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.SENDER;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationDataDump;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationDataDumpAsString;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.generateNotificationIdsDump;
import static de.gematik.demis.bulk.inbound.service.api.BatchUploadController.HTTP_HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.api.BatchUploadController.HTTP_HEADER_X_DOCUMENT_IDS;
import static de.gematik.demis.bulk.inbound.service.api.BatchUploadController.HTTP_HEADER_X_SENDER;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.bulk.inbound.service.service.BatchManagementService.BATCH_ALREADY_CLOSED_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.messaging.InQueueMessageHandler;
import de.gematik.demis.bulk.inbound.service.messaging.messages.CloseBatchMessage;
import de.gematik.demis.bulk.inbound.service.service.BatchManagementService;
import de.gematik.demis.bulk.inbound.service.test.TestContainer;
import de.gematik.demis.bulk.inbound.service.test.TestWithPostgresContainer;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import feign.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.server-url=" + BatchUploadSystemTest.EXTERNAL_SERVER_URL)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles({"test", "test-config"})
@Slf4j
class BatchUploadSystemTest extends TestWithPostgresContainer {

  public static final String EXTERNAL_SERVER_URL = "https://ingress.local/";

  private static final String ENDPOINT = "/batch/fhir/bundle";
  private static final String ENDPOINT_CLOSE = ENDPOINT + "/{id}/$close";
  private static final String ENDPOINT_STATISTICS = ENDPOINT + "/{id}/$statistics";
  private static final String UPLOAD_PATH = "/batch/upload/{id}";

  @ServiceConnection @Container
  private static final RabbitMQContainer RABBIT_MQ_CONTAINER = TestContainer.RABBIT_MQ_CONTAINER;

  public static final int NUMBER_OF_MESSAGES = 3;

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private AESEncryptionService encryptionService;
  @Autowired private FhirContext fhirContext;
  @MockitoSpyBean private BatchManagementService batchManagementService;
  @MockitoSpyBean private InQueueMessageHandler inQueueMessageHandler;
  @MockitoBean private WafServiceClient wafClient;

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void shouldProcessBatchUpload(int amount) {
    final BatchData batchData = startBatch();

    final String docIds = generateNotificationIdsDump(amount);
    final List<String> notifications = generateNotificationDataDump(amount);
    final String payload = String.join("\n", notifications);
    final HttpEntity<String> request = new HttpEntity<>(payload, allUploadHeaders(docIds));

    mockSendNotificationToWafCall();

    final ResponseEntity<Void> response =
        restTemplate.postForEntity(batchData.uploadUrl(), request, Void.class, batchData.batchId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.captor();

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                verify(inQueueMessageHandler, times(amount))
                    .processInQueueMessage(messageCaptor.capture()));

    final List<Message> messages = messageCaptor.getAllValues();
    for (final Message message : messages) {
      final byte[] messageBody = message.getBody();
      assertThat(messageBody).isNotNull();
      final String decryptedMessage = encryptionService.decryptData(messageBody);

      final Map<String, Object> messageHeaders = message.getMessageProperties().getHeaders();
      final String docId = String.valueOf(messageHeaders.get(HEADER_DOCUMENT_ID));
      final byte[] encryptedAuthorization = (byte[]) messageHeaders.get(HEADER_AUTHORIZATION);
      final String authorization = encryptionService.decryptData(encryptedAuthorization);
      assertAll(
          () -> assertThat(decryptedMessage).isNotBlank(),
          () -> assertThat(notifications).contains(decryptedMessage),
          () -> assertThat(messageHeaders).containsKey(HEADER_MESSAGE_ID),
          () ->
              assertDoesNotThrow(
                  () -> UUID.fromString(messageHeaders.get(HEADER_MESSAGE_ID).toString())),
          () -> assertThat(messageHeaders).containsEntry(HEADER_BATCH_ID, batchData.batchId()),
          () ->
              assertThat(messageHeaders)
                  .containsEntry(HEADER_FHIR_PACKAGE_VERSION, FHIR_PACKAGE_VERSION),
          () -> assertThat(messageHeaders).containsEntry(HEADER_FHIR_PACKAGE, PACKAGE),
          () -> assertThat(authorization).isEqualTo(AUTHORIZATION),
          () -> assertThat(Integer.parseInt(docId)).isBetween(0, amount - 1));
    }
  }

  @Test
  void startBatchAndPermissionCheck() {
    final BatchData batchData = startBatch();
    uploadToBatch(batchData.uploadUrl());

    // other user is not allowed to upload to this batch
    final var forbiddenHeaders = allUploadHeaders("two");
    final String hacker = "hacker";
    forbiddenHeaders.set(HTTP_HEADER_X_SENDER, hacker);
    final var forbiddenRequest =
        new HttpEntity<>(generateNotificationDataDumpAsString(1), forbiddenHeaders);
    final ResponseEntity<String> forbiddenResponse =
        restTemplate.postForEntity(batchData.uploadUrl(), forbiddenRequest, String.class);
    assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    final String expectedMessage =
        "Batch %s is not permitted for %s".formatted(batchData.batchId(), hacker);
    assertThat(forbiddenResponse.getBody())
        .contains("BATCH_NOT_PERMITTED")
        .contains(expectedMessage);
  }

  private BatchData startBatch() {
    // create new batch
    final String requestBundle =
        """
              {"resourceType" : "Bundle", "type" : "batch"}
            """;
    final var createHeaders = new HttpHeaders();
    createHeaders.setContentType(MediaType.APPLICATION_JSON);
    createHeaders.set(HTTP_HEADER_X_SENDER, SENDER);

    final ResponseEntity<String> createResponse =
        restTemplate.postForEntity(
            ENDPOINT, new HttpEntity<>(requestBundle, createHeaders), String.class);
    log.info("Create Response: {}", createResponse.getBody());

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    final Bundle responseBundle =
        fhirContext.newJsonParser().parseResource(Bundle.class, createResponse.getBody());
    final String batchId = responseBundle.getIdPart();
    final String uploadUrl = responseBundle.getLink("edit").getUrl();
    log.info("-> BatchId: {}, UploadUrl: {}", batchId, uploadUrl);
    assertThat(batchId).isNotBlank();
    assertDoesNotThrow(() -> UUID.fromString(batchId), "batchId must be an UUID: " + batchId);
    assertThat(uploadUrl).isNotBlank().startsWith(EXTERNAL_SERVER_URL).contains(batchId);
    final String localUploadUrl =
        uploadUrl.replace(EXTERNAL_SERVER_URL, "http://localhost:" + port + "/");
    return new BatchData(batchId, localUploadUrl);
  }

  private void uploadToBatch(final String localUploadUrl) {
    mockSendNotificationToWafCall();
    final var uploadRequest =
        new HttpEntity<>(generateNotificationDataDumpAsString(1), allUploadHeaders("one"));
    final ResponseEntity<Void> uploadResponse =
        restTemplate.postForEntity(localUploadUrl, uploadRequest, Void.class);
    assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void uploadToNonExistingBatchIsForbidden() {
    final String notExistingBatchId = UUID.randomUUID().toString();
    final var uploadRequest =
        new HttpEntity<>(generateNotificationDataDumpAsString(1), allUploadHeaders("one"));
    final ResponseEntity<String> response =
        restTemplate.postForEntity(UPLOAD_PATH, uploadRequest, String.class, notExistingBatchId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    final String expectedMessage = "Batch %s does not exist.".formatted(notExistingBatchId);
    assertThat(response.getBody()).contains("BATCH_NOT_FOUND").contains(expectedMessage);
  }

  private HttpHeaders allUploadHeaders(final String docIds) {
    final var headers = new HttpHeaders();
    headers.add(HTTP_HEADER_X_DOCUMENT_IDS, docIds);
    headers.add(HTTP_HEADER_X_SENDER, SENDER);
    headers.add(HTTP_HEADER_AUTHORIZATION, AUTHORIZATION);
    headers.add(HEADER_FHIR_PACKAGE_VERSION, FHIR_PACKAGE_VERSION);
    headers.add(HEADER_FHIR_PACKAGE, PACKAGE);
    headers.setContentType(MediaType.parseMediaType("application/fhir+ndjson"));
    return headers;
  }

  @Test
  @SneakyThrows
  void shouldCloseBatch() {
    when(wafClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Response.builder().status(202).request(MOCK_REQUEST).build());
    when(wafClient.sendStatusToWaf(any(), any(), any()))
        .thenReturn(Response.builder().status(202).request(MOCK_REQUEST).build());

    BatchData batchData = startBatch();
    for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
      uploadToBatch(batchData.uploadUrl());
    }

    final var httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    httpHeaders.set(HTTP_HEADER_X_SENDER, SENDER);

    final ResponseEntity<String> closeResponse =
        restTemplate.postForEntity(
            ENDPOINT_CLOSE, new HttpEntity<>("", httpHeaders), String.class, batchData.batchId());

    assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // assert http response
    HttpHeaders responseHeaders = closeResponse.getHeaders();
    assertThat(responseHeaders).isNotNull();
    assertThat(responseHeaders.containsHeader(HttpHeaders.CONTENT_LOCATION)).isTrue();
    String expectedContentLocation =
        EXTERNAL_SERVER_URL + ENDPOINT_STATISTICS.replace("{id}", batchData.batchId());
    expectedContentLocation = expectedContentLocation.replace("local//", "local/");
    assertThat(responseHeaders.getFirst(HttpHeaders.CONTENT_LOCATION))
        .isEqualTo(expectedContentLocation);

    assertThat(responseHeaders.containsHeader(HttpHeaders.RETRY_AFTER)).isTrue();
    assertThat(responseHeaders.getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");

    // assert status message sent to WAF
    final ArgumentCaptor<CloseBatchMessage> closeBatchMessageArgumentCaptor =
        ArgumentCaptor.forClass(CloseBatchMessage.class);
    final ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(wafClient)
        .sendStatusToWaf(
            closeBatchMessageArgumentCaptor.capture(),
            eq(batchData.batchId()),
            messageIdCaptor.capture());

    assertThat(closeBatchMessageArgumentCaptor.getValue())
        .returns(NUMBER_OF_MESSAGES, CloseBatchMessage::numberOfMessages);
    assertThat(messageIdCaptor.getValue()).isNotNull();
    assertDoesNotThrow(() -> UUID.fromString(messageIdCaptor.getValue()));
  }

  @Test
  void shouldThrowExceptionWhenSendingToAlreadyClosedBatch() {
    final BatchData batchData = startBatch();
    final String batchId = batchData.batchId();
    assertThat(batchId).isNotNull();

    // close batch
    when(wafClient.sendStatusToWaf(any(), any(), any()))
        .thenReturn(Response.builder().status(202).request(MOCK_REQUEST).build());

    final var httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    httpHeaders.set(HTTP_HEADER_X_SENDER, SENDER);

    final ResponseEntity<String> closeResponse =
        restTemplate.postForEntity(
            ENDPOINT_CLOSE, new HttpEntity<>("", httpHeaders), String.class, batchId);
    assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // sending messages to closed batch should be forbidden
    final var uploadRequest =
        new HttpEntity<>(generateNotificationDataDumpAsString(1), allUploadHeaders("one"));
    final ResponseEntity<String> forbiddenResponse =
        restTemplate.postForEntity(batchData.uploadUrl(), uploadRequest, String.class);
    assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbiddenResponse.getBody())
        .contains("BATCH_ALREADY_CLOSED")
        .contains(String.format(BATCH_ALREADY_CLOSED_MSG, batchId));
  }

  private void mockSendNotificationToWafCall() {
    final Response wafResponseMock = mock(Response.class);
    when(wafResponseMock.status()).thenReturn(202);
    when(wafClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(wafResponseMock);
  }
}

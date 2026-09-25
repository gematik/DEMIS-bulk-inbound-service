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
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MESSAGE_ID;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.MOCK_REQUEST;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.PACKAGE;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.EXCHANGE;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.IN_QUEUE;
import static de.gematik.demis.bulk.inbound.service.config.RabbitConfig.ROUTING_KEY_IN;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.bulk.inbound.service.connection.WafServiceClient;
import de.gematik.demis.bulk.inbound.service.messaging.InQueueMessageHandler;
import de.gematik.demis.bulk.inbound.service.repository.BatchRepository;
import de.gematik.demis.bulk.inbound.service.test.TestContainer;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import feign.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles({"test", "test-config", "without-database"})
@DirtiesContext
@Execution(
    value = ExecutionMode.SAME_THREAD,
    reason = "all testcases use the same rabbit queue. sequentiell execution is required")
@Slf4j
class InQueueMessageHandlerRetrySystemTest {
  private static final int MAX_SPRING_RETRIES = 5;
  private static final String PAYLOAD = "my test notification message";
  private static final Response WAF_CLIENT_RESPONSE_ACCEPTED =
      Response.builder().status(202).request(MOCK_REQUEST).build();

  @MockitoBean BatchRepository batchRepositoryMock;
  @MockitoBean WafServiceClient wafClient;
  @MockitoSpyBean InQueueMessageHandler messageListener;
  @Autowired RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;
  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired AESEncryptionService encryptionService;

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private QueueStats queueStatsBaseline;

  @ServiceConnection @Container
  private static final RabbitMQContainer RABBIT_MQ_CONTAINER = TestContainer.RABBIT_MQ_CONTAINER;

  private record QueueStats(
      long publish, long redeliver, int messages, int messagesReady, int messagesUnacknowledged) {}

  @BeforeEach
  void cleanupQueue() {
    log.info("stopping rabbit listeners before purge");
    rabbitListenerEndpointRegistry.getListenerContainers().forEach(MessageListenerContainer::stop);

    try {
      await()
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(50, TimeUnit.MILLISECONDS)
          .until(() -> getQueueStats().messagesUnacknowledged() == 0);

      log.info("purge old messages from queue");
      // note: only ready messages are deleted. Unacked messages are not deleted.
      purgeQueue();

      await()
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(50, TimeUnit.MILLISECONDS)
          .until(() -> getQueueStats().messages == 0);

      queueStatsBaseline = getQueueStats();
      log.info("message queue is empty. baseline={}", queueStatsBaseline);
    } finally {
      Mockito.reset(wafClient, messageListener);
      rabbitListenerEndpointRegistry
          .getListenerContainers()
          .forEach(MessageListenerContainer::start);
      log.info("rabbit listeners restarted");
    }
  }

  @Test
  void missingRequiredHeader_should_noRetry_nack_reject() {
    final Message invalidMessage = validMessage();
    invalidMessage.getMessageProperties().getHeaders().remove(HEADER_FHIR_PACKAGE);

    publishMessage(invalidMessage);
    waitUntilMessageIsProcessedOrRedelivered();

    verify(messageListener, times(1)).processInQueueMessage(any());
    verifyNoInteractions(wafClient);
  }

  @Test
  void decryptionError_should_noRetry_sendErrorMessage_ack() {
    when(wafClient.sendErrorToWaf(any(), any(), any(), any()))
        .thenReturn(WAF_CLIENT_RESPONSE_ACCEPTED);

    final AESEncryptionService encryptionServiceWithWrongKey =
        new AESEncryptionService("hjsakjdfhdksawo2".getBytes());
    final Message message =
        buildMessage(
            encryptionServiceWithWrongKey.encryptData(PAYLOAD),
            encryptionServiceWithWrongKey.encryptData(AUTHORIZATION));

    publishMessage(message);
    waitUntilMessageIsProcessedOrRedelivered();

    verify(wafClient).sendErrorToWaf(any(), any(), any(), any());
    verifyNoMoreInteractions(wafClient);
  }

  @Test
  void short_wafClientError_should_retry_ack() {
    // note spring retry must be => 2
    when(wafClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("just for testing retries 1"))
        .thenThrow(new RuntimeException("just for testing retries 2"))
        .thenReturn(WAF_CLIENT_RESPONSE_ACCEPTED);
    final int expectedCalls = 3; // 2 unsuccessful and 1 successful calls

    publishMessage(validMessage());
    waitUntilMessageIsProcessedOrRedelivered();

    verify(wafClient, times(expectedCalls))
        .sendNotificationToWaf(
            PAYLOAD,
            MESSAGE_ID,
            BATCH_ID,
            DOCUMENT_ID,
            AUTHORIZATION,
            FHIR_PACKAGE_VERSION,
            PACKAGE);
    // do not send waf error message
    verifyNoMoreInteractions(wafClient);

    // check rabbit listener calls
    final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.captor();
    verify(messageListener, times(expectedCalls)).processInQueueMessage(messageCaptor.capture());
    // check that the message is not requeued for retrying
    assertThat(messageCaptor.getAllValues())
        .extracting(msg -> msg.getMessageProperties().getRedelivered())
        .containsOnly(false);
  }

  @Test
  void long_wafClientError_should_retry_nack_requeue() {
    // all wafclient calls fail
    when(wafClient.sendNotificationToWaf(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("just for testing retries"));

    publishMessage(validMessage());
    waitUntilMessageIsProcessedOrRedelivered();

    final int expectedAtLeastCalls =
        1 // first execution
            + MAX_SPRING_RETRIES // retries without requeueing
            + 1; // message is requeued and processed again

    verify(wafClient, atLeast(expectedAtLeastCalls))
        .sendNotificationToWaf(
            PAYLOAD,
            MESSAGE_ID,
            BATCH_ID,
            DOCUMENT_ID,
            AUTHORIZATION,
            FHIR_PACKAGE_VERSION,
            PACKAGE);
    // do not send waf error message
    verifyNoMoreInteractions(wafClient);

    // check rabbit listener calls
    final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.captor();
    verify(messageListener, atLeast(expectedAtLeastCalls))
        .processInQueueMessage(messageCaptor.capture());
    // check that the message is requeued after all spring retries are failed
    List<Boolean> redelivered =
        messageCaptor.getAllValues().stream()
            .map(msg -> msg.getMessageProperties().getRedelivered())
            .toList();
    assertThat(redelivered.subList(0, 1 + MAX_SPRING_RETRIES)).containsOnly(false);
    assertThat(redelivered.subList(1 + MAX_SPRING_RETRIES, redelivered.size())).containsOnly(true);
  }

  private void publishMessage(final Message message) {
    rabbitTemplate.send(EXCHANGE, ROUTING_KEY_IN, message);
  }

  private Message validMessage() {
    return buildMessage(
        encryptionService.encryptData(PAYLOAD), encryptionService.encryptData(AUTHORIZATION));
  }

  /**
   * wait until message was publish to queue and processed (note: messages = ready + unacked) or
   * redelivered at least once. Since we have an infinitive requeue loop we stop after one full
   * roundtrip
   */
  private void waitUntilMessageIsProcessedOrRedelivered() {
    log.info("Queue Statistics Baseline = {}", queueStatsBaseline);
    try {
      await()
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(50, TimeUnit.MILLISECONDS)
          .until(
              () -> {
                final QueueStats counts = getQueueStats();
                return counts.publish > queueStatsBaseline.publish
                    && (counts.messages == 0
                        || (counts.redeliver - queueStatsBaseline.redeliver) > 1);
              });
    } finally {
      log.info("queue statistics after waiting = {}", getQueueStats());
    }
  }

  @SneakyThrows
  private QueueStats getQueueStats() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(RABBIT_MQ_CONTAINER.getHttpUrl() + "/api/queues/%2F/" + IN_QUEUE))
            .header("Authorization", getRabbitAuthorization())
            .GET()
            .build();
    final String json = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    final var root = new ObjectMapper().readTree(json);

    return new QueueStats(
        root.path("message_stats").path("publish").asInt(0),
        root.path("message_stats").path("redeliver").asInt(0),
        root.path("messages").asInt(0),
        root.path("messages_ready").asInt(0),
        root.path("messages_unacknowledged").asInt(0));
  }

  @SneakyThrows
  private void purgeQueue() {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    RABBIT_MQ_CONTAINER.getHttpUrl() + "/api/queues/%2F/" + IN_QUEUE + "/contents"))
            .header("Authorization", getRabbitAuthorization())
            .DELETE()
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .withFailMessage("purge failed: status=%s body=%s", response.statusCode(), response.body())
        .isBetween(200, 299);
  }

  private String getRabbitAuthorization() {
    final String credentials =
        RABBIT_MQ_CONTAINER.getAdminUsername() + ":" + RABBIT_MQ_CONTAINER.getAdminPassword();
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  private Message buildMessage(final byte[] payloadEncrypted, final byte[] authorizationEnrypted) {
    return MessageBuilder.withBody(payloadEncrypted)
        .andProperties(
            MessagePropertiesBuilder.newInstance()
                .setHeader(HEADER_MESSAGE_ID, MESSAGE_ID)
                .setHeader(HEADER_BATCH_ID, BATCH_ID)
                .setHeader(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .setHeader(HEADER_AUTHORIZATION, authorizationEnrypted)
                .setHeader(HEADER_FHIR_PACKAGE_VERSION, FHIR_PACKAGE_VERSION)
                .setHeader(HEADER_FHIR_PACKAGE, PACKAGE)
                .build())
        .build();
  }
}

package de.gematik.demis.bulk.inbound.service;

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

import feign.Request;
import feign.Request.HttpMethod;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.NonNull;

public class TestdataGenerator {

  public static final String FHIR_PACKAGE_VERSION = "V1";
  public static final String PACKAGE = "package";
  public static final String MESSAGE_ID = UUID.randomUUID().toString();
  public static final String BATCH_ID = UUID.randomUUID().toString();
  // Important to be a long string to test the handling of long authorization headers, as they can
  // occur in production and caused issues in reading messages from the queue
  public static final String AUTHORIZATION =
      "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJsS2VZeHdrbTBFdEN5eTJLUWhPZ0lvelZRYWlHX1BRZGZobFZOeW5yTTVFIn0.eyJleHAiOjE3NzA2NDQxOTcsImlhdCI6MTc3MDY0MzU5NywianRpIjoib25ydHJvOmU2YzE4YjY1LThjYzAtNDQ5Ny1hYWYxLTgxYmFkMDM5MzMyOSIsImlzcyI6Imh0dHBzOi8vYXV0aC5pbmdyZXNzLmxvY2FsL3JlYWxtcy9MQUIiLCJzdWIiOiJjZTRkN2ExYy0zNGUxLTQ1MjQtYjY2OC1kNzg1NDg1ODI0YTQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJkZW1pcy1hZGFwdGVyIiwic2lkIjoiYzM4Yjg5ZmQtOTNjNS00OTNiLTkxYTAtNzFlZTdkOGY5ZjE2IiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRpc2Vhc2Utbm90aWZpY2F0aW9uLW5vbm5vbWluYWwtc2VuZGVyIiwiZGlzZWFzZS1ub3RpZmljYXRpb24tc2VuZGVyIiwicGF0aG9nZW4tbm90aWZpY2F0aW9uLW5vbm5vbWluYWwtc2VuZGVyIiwicGF0aG9nZW4tbm90aWZpY2F0aW9uLXNlbmRlciIsInBhdGhvZ2VuLW5vdGlmaWNhdGlvbi1uZWdhdGl2ZS1zZW5kZXIiLCJhcnMtZGF0YS1zZW5kZXIiXX0sInNjb3BlIjoicHJvZmlsZSIsInByZWZlcnJlZF91c2VybmFtZSI6IjMzMzMzIn0.lCxv90BCbUV98WuBSUz7iQD0BkhcD1WW9O-seWvyT1JJXGj6W5TN33XB31QSMiMj57bFGAJOwFVJT8YpgONOLw9Ifh3GXpgCUmJfUMwwJFCnfreh0u0Oe_joh0-ougEQ1WHoiYN3ICI95zLD_NNW3wiXrf_tALZpPfx3jPaFUI23CAiBkvvHlPlz5Rkx7xhECmqJ7SQBtbFB7I7z_Isi7uRIMt9OAOHUt9VX6OYoqC_QZNDj5lKZBhBm_ilOpMVwI_2WaaE9ofmoNlinWJ77w5acYNx2q_MNpRM4BcbnqUiLJkBi8gdYTvidm4ruVWo80pXlu5L7Z1xg_popPTUGiQ";
  public static final String SENDER = "labor-4711";
  public static final String NOTIFICATION_PREFIX = "notificationPrefix-";
  public static final String DOCUMENT_ID = UUID.randomUUID().toString();
  public static final Request MOCK_REQUEST =
      Request.create(
          HttpMethod.POST,
          "https://localhost:8080",
          Map.of(),
          "someBody".getBytes(),
          StandardCharsets.UTF_8);

  public static String generateNotificationIdsDump(int amount) {
    return String.join(",", IntStream.range(0, amount).mapToObj(Integer::toString).toList());
  }

  public static BufferedReader generateNotificationDataDumpReaderFromNotifications(
      List<String> notifications) {
    if (notifications.isEmpty()) {
      return new BufferedReader(new StringReader(""));
    }
    return new BufferedReader(new StringReader(String.join("\n", notifications)));
  }

  public static BufferedReader generateNotificationDataDumpReader(int amount) {
    return new BufferedReader(new StringReader(generateNotificationDataDumpAsString(amount)));
  }

  public static String generateNotificationDataDumpAsString(int amount) {
    return String.join("\n", generateNotificationDataDump(amount));
  }

  public static List<String> generateNotificationDataDump(int amount) {
    if (amount == 0) {
      return List.of();
    }
    List<String> notifications = new ArrayList<>();
    final String notification = getNotificationData(DOCUMENT_ID);
    notifications.add(notification);
    IntStream.range(1, amount)
        .forEach(i -> notifications.add(getNotificationData(Integer.toString(i))));
    return notifications;
  }

  private static @NonNull String getNotificationData(String documentId) {
    return new NotificationData(
            NOTIFICATION_PREFIX + documentId,
            MESSAGE_ID,
            BATCH_ID,
            documentId,
            AUTHORIZATION,
            FHIR_PACKAGE_VERSION,
            PACKAGE)
        .toString()
        .replaceAll("\\n", "");
  }

  private record NotificationData(
      String notification,
      String messageId,
      String batchId,
      String documentId,
      String authorization,
      String apiVersion,
      String profileVersion) {

    @Override
    public @NonNull String toString() {
      return "{"
          + "\"messageId\":\""
          + messageId
          + "\","
          + "\"notification\":\""
          + notification
          + "\","
          + "\"batchId\":\""
          + batchId
          + "\","
          + "\"documentId\":\""
          + documentId
          + "\","
          + "\"authorization\":\""
          + authorization
          + "\","
          + "\"apiVersion\":\""
          + apiVersion
          + "\","
          + "\"profileVersion\":\""
          + profileVersion
          + "\""
          + "}";
    }
  }
}

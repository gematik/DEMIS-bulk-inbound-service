package de.gematik.demis.bulk.inbound.service.api;

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

import static de.gematik.demis.bulk.inbound.service.api.BatchManagementController.BATCH_MANAGEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.bulk.inbound.service.service.BatchFhirService;
import de.gematik.demis.bulk.inbound.service.service.BatchManagementService;
import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.error.FhirErrorResponseAutoConfiguration;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BatchManagementController.class)
@ImportAutoConfiguration({
  ErrorHandlerConfiguration.class,
  FhirSupportAutoConfiguration.class,
  FhirErrorResponseAutoConfiguration.class
})
class BatchManagementControllerIntegrationTest {

  private static final String ENDPOINT_CLOSE =
      BATCH_MANAGEMENT_PATH + "/550e8400-e29b-41d4-a716-446655440000/$close";
  private static final String HEADER_X_SENDER = "x-sender";
  private static final String HEADER_AUTHORIZATION = "Bearer some_token";
  private static final String SERVER_URL = "http://localhost:8080";
  private static final String SENDER = "test-sender";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BatchManagementService batchManagementService;
  @MockitoBean private BatchFhirService batchFhirService;

  @Test
  void shouldStartBatch() throws Exception {
    final String requestBody = "valid fhir-bundle";
    final UUID batchId = UUID.randomUUID();
    final String uploadUrl = SERVER_URL + "/batch/upload/" + batchId;

    when(batchManagementService.startBatch(any())).thenReturn(batchId);

    final Bundle bundle = new Bundle();
    bundle.setId(batchId.toString());
    when(batchFhirService.createStartResponse(any(), anyString())).thenReturn(bundle);

    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .header(HEADER_X_SENDER, SENDER)
                .contentType("application/fhir+json")
                .content(requestBody))
        .andExpectAll(
            status().isCreated(),
            content().contentTypeCompatibleWith("application/fhir+json"),
            jsonPath("$.resourceType").value("Bundle"),
            jsonPath("$.id").value(batchId.toString()));

    final var senderCaptor = ArgumentCaptor.forClass(String.class);
    verify(batchManagementService).startBatch(senderCaptor.capture());
    final var batchIdCaptor = ArgumentCaptor.forClass(UUID.class);
    final var uploadUrlCaptor = ArgumentCaptor.forClass(String.class);
    verify(batchFhirService)
        .createStartResponse(batchIdCaptor.capture(), uploadUrlCaptor.capture());

    assertThat(senderCaptor.getValue()).isEqualTo(SENDER);
    assertThat(batchIdCaptor.getValue()).isEqualTo(batchId);
    assertThat(uploadUrlCaptor.getValue()).isEqualTo(uploadUrl);
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/fhir+json", "application/json+fhir", "application/json"})
  void validContentTypesForStartBatch(final String contentType) throws Exception {
    when(batchManagementService.startBatch(any())).thenReturn(UUID.randomUUID());
    when(batchFhirService.createStartResponse(any(), anyString())).thenReturn(new Bundle());
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .header(HEADER_X_SENDER, SENDER)
                .contentType(contentType)
                .content("{}"))
        .andExpectAll(
            status().isCreated(), content().contentTypeCompatibleWith("application/fhir+json"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/fhir+json", "application/json+fhir", "application/json", "*/*", ""})
  void validAcceptedForStartBatch(final String accepted) throws Exception {
    when(batchManagementService.startBatch(any())).thenReturn(UUID.randomUUID());
    when(batchFhirService.createStartResponse(any(), anyString())).thenReturn(new Bundle());
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .header(HEADER_X_SENDER, SENDER)
                .contentType("application/fhir+json")
                .accept(accepted)
                .content("{}"))
        .andExpectAll(
            status().isCreated(), content().contentTypeCompatibleWith("application/fhir+json"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/fhir+ndjson",
        "application/ndjson",
        "application/x-ndjson",
        "application/fhir+xml",
        "application/xml+fhir",
        "application/xml",
        "text/plain",
        ""
      })
  void invalidContentTypesForStartBatch(final String contentType) throws Exception {
    final String expectedResultContentType =
        contentType.contains("xml") ? "application/fhir+xml" : "application/fhir+json";
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .header(HEADER_X_SENDER, SENDER)
                .contentType(contentType)
                .content("irrelevant"))
        .andExpectAll(
            status().isUnsupportedMediaType(),
            content().contentTypeCompatibleWith(expectedResultContentType));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/fhir+xml", "application/xml+fhir", "application/xml", "text/plain"})
  void notAcceptedForStartBatch(final String accepted) throws Exception {
    final String expectedResultContentType =
        accepted.contains("xml") ? "application/fhir+xml" : "application/fhir+json";
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .header(HEADER_X_SENDER, SENDER)
                .contentType("application/fhir+json")
                .accept(accepted)
                .content("{}"))
        .andExpectAll(
            status().isNotAcceptable(),
            content().contentTypeCompatibleWith(expectedResultContentType));
  }

  @ParameterizedTest
  @ValueSource(strings = {BATCH_MANAGEMENT_PATH, ENDPOINT_CLOSE})
  void headerSenderIsRequired() throws Exception {
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH)
                .contentType("application/fhir+json")
                .header(AUTHORIZATION, HEADER_AUTHORIZATION)
                .content("irrelevant"))
        .andExpectAll(
            status().isBadRequest(),
            content().contentTypeCompatibleWith("application/fhir+json"),
            jsonPath("$.resourceType").value("OperationOutcome"),
            jsonPath("$.issue[0].diagnostics").value("Required header 'x-sender' is not present."));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/fhir+json", "application/json+fhir", "application/json"})
  void shouldCloseBatch(final String contentType) throws Exception {
    final UUID batchId = UUID.randomUUID();

    doNothing().when(batchManagementService).checkPermission(SENDER, batchId);
    doNothing().when(batchManagementService).closeBatch(batchId);

    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH + "/" + batchId + "/$close")
                .contentType(contentType)
                .header(HEADER_X_SENDER, SENDER))
        .andExpectAll(
            status().isAccepted(),
            header()
                .string(
                    HttpHeaders.CONTENT_LOCATION,
                    SERVER_URL + BATCH_MANAGEMENT_PATH + "/" + batchId + "/$statistics"),
            header().string(HttpHeaders.RETRY_AFTER, "30"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/fhir+ndjson",
        "application/ndjson",
        "application/x-ndjson",
        "application/fhir+xml",
        "application/xml+fhir",
        "application/xml",
        "text/plain",
        ""
      })
  void invalidContentTypesForCloseBatch(final String contentType) throws Exception {
    final String expectedResultContentType =
        contentType.contains("xml") ? "application/fhir+xml" : "application/fhir+json";
    final UUID batchId = UUID.randomUUID();
    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH + "/" + batchId + "/$close")
                .contentType(contentType)
                .header(HEADER_X_SENDER, SENDER))
        .andExpectAll(
            status().isUnsupportedMediaType(),
            content().contentTypeCompatibleWith(expectedResultContentType));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/fhir+json", "application/json+fhir", "application/json", "*/*", ""})
  void validAcceptedForCloseBatch(final String accepted) throws Exception {
    final UUID batchId = UUID.randomUUID();

    doNothing().when(batchManagementService).checkPermission(SENDER, batchId);
    doNothing().when(batchManagementService).closeBatch(batchId);

    mockMvc
        .perform(
            post(BATCH_MANAGEMENT_PATH + "/" + batchId + "/$close")
                .contentType("application/fhir+json")
                .accept(accepted)
                .header(HEADER_X_SENDER, SENDER))
        .andExpectAll(
            status().isAccepted(),
            header()
                .string(
                    HttpHeaders.CONTENT_LOCATION,
                    SERVER_URL + BATCH_MANAGEMENT_PATH + "/" + batchId + "/$statistics"),
            header().string(HttpHeaders.RETRY_AFTER, "30"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/fhir+xml", "application/xml+fhir", "application/xml", "text/plain"})
  void notAcceptedForCloseBatch(final String notAccepted) throws Exception {
    final String expectedResultContentType =
        notAccepted.contains("xml") ? "application/fhir+xml" : "application/fhir+json";
    mockMvc
        .perform(
            post(ENDPOINT_CLOSE)
                .contentType("application/fhir+json")
                .accept(notAccepted)
                .header(HEADER_X_SENDER, SENDER))
        .andExpectAll(
            status().isNotAcceptable(),
            content().contentTypeCompatibleWith(expectedResultContentType));
  }
}

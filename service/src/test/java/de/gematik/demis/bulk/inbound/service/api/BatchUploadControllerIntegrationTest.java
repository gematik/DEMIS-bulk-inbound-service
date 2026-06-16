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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.bulk.inbound.service.service.BatchManagementService;
import de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService;
import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.error.FhirErrorResponseAutoConfiguration;
import java.io.BufferedReader;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(BatchUploadController.class)
@ImportAutoConfiguration({
  ErrorHandlerConfiguration.class,
  FhirSupportAutoConfiguration.class,
  FhirErrorResponseAutoConfiguration.class
})
class BatchUploadControllerIntegrationTest {

  private static final String ENDPOINT = "/batch/upload/{id}";

  private static final String HEADER_X_SENDER = "x-sender";
  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String HEADER_X_DOCUMENT_IDS = "X-Document-Ids";

  private static final String VALID_CONTENT_TYPE = "application/fhir+ndjson";

  private static final String JWT_TOKEN = "eyFakeToken";
  private static final String DOC_IDS = "1,2";
  private static final String SENDER = "tester";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private BatchManagementService batchManagementService;
  @MockitoBean private UploadBatchService uploadBatchService;

  @Test
  void okay() throws Exception {
    final String body = "{Bundle-1}\n{Bundle-2}";
    final UUID batchId = UUID.randomUUID();

    mockMvc.perform(request(batchId.toString(), body)).andExpect(status().isAccepted());

    verify(batchManagementService).checkPermission(SENDER, batchId);

    final var readerCaptor = ArgumentCaptor.forClass(BufferedReader.class);
    verify(uploadBatchService)
        .processBatch(eq(batchId.toString()), eq(JWT_TOKEN), eq(DOC_IDS), readerCaptor.capture());
    assertThat(readerCaptor.getValue().lines().toList())
        .containsExactly("{Bundle-1}", "{Bundle-2}");
  }

  @Test
  void notPermitted() throws Exception {
    final UUID batchId = UUID.randomUUID();

    doThrow(ErrorCode.BATCH_NOT_PERMITTED.exception("just for testing"))
        .when(batchManagementService)
        .checkPermission(SENDER, batchId);

    mockMvc
        .perform(request(batchId.toString(), "body"))
        .andExpectAll(
            status().isForbidden(),
            content().contentTypeCompatibleWith("application/fhir+json"),
            jsonPath("$.resourceType").value("OperationOutcome"));
  }

  @Test
  void badRequest_invalidBatchId() throws Exception {
    final String invalidBatchId = "42";

    mockMvc
        .perform(request(invalidBatchId, "body"))
        .andExpectAll(
            status().isBadRequest(),
            content().contentTypeCompatibleWith("application/fhir+json"),
            jsonPath("$.resourceType").value("OperationOutcome"),
            jsonPath("$.issue[0].diagnostics")
                .value("Failed to convert 'id' with value: '" + invalidBatchId + "'"));
  }

  @ParameterizedTest
  @ValueSource(strings = {HEADER_AUTHORIZATION, HEADER_X_SENDER, HEADER_X_DOCUMENT_IDS})
  void badRequest_missingRequiredHeader(final String missingHeader) throws Exception {
    final var headers = requiredHeaders();
    headers.remove(missingHeader);
    mockMvc
        .perform(
            post(ENDPOINT, UUID.randomUUID().toString())
                .headers(headers)
                .contentType(VALID_CONTENT_TYPE)
                .content("body"))
        .andExpectAll(
            status().isBadRequest(),
            content().contentTypeCompatibleWith("application/fhir+json"),
            jsonPath("$.resourceType").value("OperationOutcome"),
            jsonPath("$.issue[0].diagnostics")
                .value("Required header '" + missingHeader + "' is not present."));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/json",
        "application/xml",
        "text/plain",
        "application/fhir+json",
        "application/fhir+xml",
        "application/x-www-form-urlencoded",
        "application/x-ndjson",
        "application/ndjson",
        "application/ndjson+fhir",
      })
  void contentTypeNotSupported(final String contentType) throws Exception {
    mockMvc
        .perform(request(UUID.randomUUID().toString(), "body").contentType(contentType))
        .andExpect(status().isUnsupportedMediaType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/fhir+ndjson"})
  void contentTypeOkay(final String contentType) throws Exception {
    mockMvc
        .perform(request(UUID.randomUUID().toString(), "body").contentType(contentType))
        .andExpect(status().is2xxSuccessful());
  }

  private MockHttpServletRequestBuilder request(final String batchId, final String body) {
    return post(ENDPOINT, batchId)
        .headers(requiredHeaders())
        .contentType(VALID_CONTENT_TYPE)
        .content(body);
  }

  private HttpHeaders requiredHeaders() {
    final var headers = new HttpHeaders();
    headers.set(HEADER_X_SENDER, SENDER);
    headers.set(HEADER_AUTHORIZATION, JWT_TOKEN);
    headers.set(HEADER_X_DOCUMENT_IDS, DOC_IDS);
    return headers;
  }
}

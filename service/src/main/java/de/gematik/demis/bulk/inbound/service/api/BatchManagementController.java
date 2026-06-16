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

import static de.gematik.demis.bulk.inbound.service.api.BatchManagementApi.BATCH_MANAGEMENT_PATH;

import de.gematik.demis.bulk.inbound.service.service.BatchFhirService;
import de.gematik.demis.bulk.inbound.service.service.BatchManagementService;
import de.gematik.demis.service.base.fhir.response.FhirResponseConverter;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(BATCH_MANAGEMENT_PATH)
@RequiredArgsConstructor
class BatchManagementController implements BatchManagementApi {

  private final BatchManagementService batchManagementService;
  private final BatchFhirService batchFhirService;
  private final FhirResponseConverter fhirResponseConverter;

  @Value("${app.server-url}")
  private URI serverUrl;

  private String determineBatchUploadLocation(final UUID batchId) {
    return UriComponentsBuilder.fromUri(serverUrl)
        .path(BatchUploadController.UPLOAD_PATH)
        .pathSegment(batchId.toString())
        .toUriString();
  }

  @Override
  public ResponseEntity<Object> startBatch(
      @RequestHeader(HTTP_HEADER_X_SENDER) final String sender,
      @RequestBody final String requestBody,
      final WebRequest webRequest) {
    batchFhirService.validateStartRequest(requestBody);
    final UUID batchId = batchManagementService.startBatch(sender);
    final String uploadLocation = determineBatchUploadLocation(batchId);
    final Bundle result = batchFhirService.createStartResponse(batchId, uploadLocation);
    return fhirResponseConverter.buildResponse(
        ResponseEntity.status(HttpStatus.CREATED), result, webRequest);
  }

  @Override
  // activate hibernate 1st level cache to avoid multiple loading same batch entity from database
  @Transactional
  public ResponseEntity<Object> closeBatch(
      @PathVariable final UUID id, @RequestHeader(HTTP_HEADER_X_SENDER) final String sender) {
    batchManagementService.checkPermission(sender, id);
    batchManagementService.closeBatch(id);
    return ResponseEntity.accepted().headers(determineHttpHeadersForBatchClose(id)).build();
  }

  private HttpHeaders determineHttpHeadersForBatchClose(UUID batchId) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(HttpHeaders.RETRY_AFTER, "30");
    httpHeaders.add(HttpHeaders.CONTENT_LOCATION, determineBatchStatisticLocation(batchId));
    return httpHeaders;
  }

  private String determineBatchStatisticLocation(UUID batchId) {
    return UriComponentsBuilder.fromUri(serverUrl)
        .path(BATCH_MANAGEMENT_PATH)
        .pathSegment(batchId.toString())
        .pathSegment("$statistics")
        .toUriString();
  }
}

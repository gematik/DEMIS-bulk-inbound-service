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

import static de.gematik.demis.bulk.inbound.service.api.BatchUploadController.UPLOAD_PATH;

import de.gematik.demis.bulk.inbound.service.service.BatchManagementService;
import de.gematik.demis.bulk.inbound.service.service.batchupload.UploadBatchService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(UPLOAD_PATH)
@RequiredArgsConstructor
public class BatchUploadController implements BatchUploadApi {

  private final BatchManagementService batchManagementService;
  private final UploadBatchService uploadBatchService;

  @Override
  public ResponseEntity<Void> processBatchRequest(
      @PathVariable final UUID id,
      @RequestHeader(HTTP_HEADER_X_SENDER) final String sender,
      @RequestHeader(HTTP_HEADER_AUTHORIZATION) final String authorization,
      @RequestHeader(HTTP_HEADER_X_DOCUMENT_IDS) final String documentIds,
      final HttpServletRequest request)
      throws IOException {
    batchManagementService.checkPermission(sender, id);
    uploadBatchService.processBatch(id.toString(), authorization, documentIds, request.getReader());
    return ResponseEntity.accepted().build();
  }
}

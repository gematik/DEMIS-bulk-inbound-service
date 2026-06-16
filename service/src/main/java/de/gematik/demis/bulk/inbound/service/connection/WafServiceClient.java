package de.gematik.demis.bulk.inbound.service.connection;

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

import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_MESSAGE_ID;

import de.gematik.demis.bulk.inbound.service.messaging.messages.CloseBatchMessage;
import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "waf-service", url = "${app.waf-url}")
public interface WafServiceClient {

  @ErrorCode("WAF")
  @PostMapping(
      value = "/process-notification",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  Response sendNotificationToWaf(
      String payload,
      @RequestHeader(HEADER_MESSAGE_ID) String messageId,
      @RequestHeader(HEADER_BATCH_ID) String batchId,
      @RequestHeader(HEADER_DOCUMENT_ID) String documentId,
      @RequestHeader(HEADER_AUTHORIZATION) String authorization,
      @RequestHeader(HEADER_FHIR_PACKAGE_VERSION) String apiVersion,
      @RequestHeader(HEADER_FHIR_PACKAGE) String profile);

  @ErrorCode("WAF")
  @PostMapping(
      value = "/process-error",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  Response sendErrorToWaf(
      String payload,
      @RequestHeader(HEADER_BATCH_ID) String batchId,
      @RequestHeader(HEADER_DOCUMENT_ID) String documentId,
      @RequestHeader(HEADER_MESSAGE_ID) String messageId);

  @ErrorCode("WAF")
  @PostMapping(
      value = "/process-status",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  Response sendStatusToWaf(
      CloseBatchMessage payload,
      @RequestHeader(HEADER_BATCH_ID) String batchId,
      @RequestHeader(HEADER_MESSAGE_ID) String messageId);
}

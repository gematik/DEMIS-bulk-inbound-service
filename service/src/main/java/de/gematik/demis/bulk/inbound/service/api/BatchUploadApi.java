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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(UPLOAD_PATH)
@Tag(
    name = "Batch Upload",
    description = "Operations for uploading FHIR documents into an existing batch.")
public interface BatchUploadApi {

  String UPLOAD_PATH = "/batch/upload";
  String HTTP_HEADER_AUTHORIZATION = "Authorization";
  String HTTP_HEADER_X_DOCUMENT_IDS = "X-Document-Ids";
  String HTTP_HEADER_X_SENDER = "x-sender";

  @PostMapping(
      value = "/{id}",
      consumes = {"application/fhir+ndjson"})
  @Operation(
      operationId = "processBatchRequest",
      summary = "Upload documents to a batch",
      description =
          "Uploads one or more FHIR documents into the batch identified by the given id. The "
              + "request body must be a newline-delimited stream of FHIR resources serialized as "
              + "application/fhir+ndjson (one resource per line). The uploaded documents are "
              + "processed asynchronously. All error responses are returned as a FHIR "
              + "OperationOutcome.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              description =
                  "Newline-delimited stream of FHIR resources, one serialized resource per line.",
              content =
                  @Content(
                      mediaType = "application/fhir+ndjson",
                      schema = @Schema(type = "string", format = "binary"),
                      examples =
                          @ExampleObject(
                              name = "Two bundles",
                              value =
                                  "{\"resourceType\":\"Bundle\"}\n{\"resourceType\":\"Bundle\"}"))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Upload accepted; documents are being processed asynchronously.",
        content = @Content),
    @ApiResponse(
        responseCode = "400",
        description =
            "Invalid request, e.g. the path `id` is not a valid UUID or a required header "
                + "(`x-sender`, `Authorization`, `X-Document-Ids`) is missing.",
        content =
            @Content(
                mediaType = "application/fhir+json",
                schema = @Schema(type = "string"),
                examples =
                    @ExampleObject(
                        name = "Missing header",
                        value =
                            "{\"resourceType\":\"OperationOutcome\","
                                + "\"issue\":[{\"severity\":\"error\",\"code\":\"required\","
                                + "\"diagnostics\":\"Required header 'x-sender' is not present.\"}]}"))),
    @ApiResponse(
        responseCode = "403",
        description = "The sender is not permitted to upload to this batch.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "415",
        description =
            "The request `Content-Type` is not supported. The only supported media type is "
                + "`application/fhir+ndjson`.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string")))
  })
  ResponseEntity<Void> processBatchRequest(
      @Parameter(
              description = "Unique identifier of the target batch.",
              required = true,
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathVariable
          final UUID id,
      @Parameter(
              name = HTTP_HEADER_X_SENDER,
              in = ParameterIn.HEADER,
              description = "Identifier of the sender uploading the documents.",
              required = true)
          @RequestHeader(HTTP_HEADER_X_SENDER)
          final String sender,
      @Parameter(
              name = HTTP_HEADER_AUTHORIZATION,
              in = ParameterIn.HEADER,
              description = "Bearer token authorizing the upload.",
              required = true,
              example = "Bearer eyJhbGciOiJ...")
          @RequestHeader(HTTP_HEADER_AUTHORIZATION)
          final String authorization,
      @Parameter(
              name = HTTP_HEADER_X_DOCUMENT_IDS,
              in = ParameterIn.HEADER,
              description = "Comma-separated list of document identifiers contained in the upload.",
              required = true,
              example = "doc-1,doc-2")
          @RequestHeader(HTTP_HEADER_X_DOCUMENT_IDS)
          final String documentIds,
      @Parameter(hidden = true) final HttpServletRequest request)
      throws IOException;
}

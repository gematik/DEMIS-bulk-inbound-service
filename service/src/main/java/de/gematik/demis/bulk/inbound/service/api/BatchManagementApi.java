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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;

@RequestMapping(BatchManagementApi.BATCH_MANAGEMENT_PATH)
@Tag(
    name = "Batch Management",
    description = "Operations for starting and closing FHIR bulk-inbound batches.")
public interface BatchManagementApi {
  String BATCH_MANAGEMENT_PATH = "/batch/fhir/bundle";
  String HTTP_HEADER_X_SENDER = "x-sender";

  @PostMapping(
      consumes = {"application/fhir+json", "application/json+fhir", APPLICATION_JSON_VALUE},
      produces = {"application/fhir+json", "application/json+fhir", APPLICATION_JSON_VALUE})
  @Operation(
      operationId = "startBatch",
      summary = "Start a new batch",
      description =
          "Creates a new batch for the given sender and returns a FHIR Bundle "
              + "containing the upload location for subsequent document uploads. "
              + "The request body must be a FHIR resource serialized as "
              + "application/fhir+json, application/json+fhir or application/json.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content = {
                @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string")),
                @Content(mediaType = "application/json+fhir", schema = @Schema(type = "string")),
                @Content(mediaType = "application/json", schema = @Schema(type = "string"))
              }))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Batch successfully created. The returned Bundle contains the upload location.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request body or a required header (e.g. x-sender) is missing.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "406",
        description =
            "The requested Accept media type is not supported. Supported media types are "
                + "application/fhir+json, application/json+fhir and application/json.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "415",
        description =
            "The request Content-Type is not supported. Supported media types are "
                + "application/fhir+json, application/json+fhir and application/json.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string")))
  })
  ResponseEntity<Object> startBatch(
      @Parameter(
              name = HTTP_HEADER_X_SENDER,
              in = ParameterIn.HEADER,
              description = "Identifier of the sender starting the batch.",
              required = true)
          @RequestHeader(HTTP_HEADER_X_SENDER)
          final String sender,
      @RequestBody final String requestBody,
      final WebRequest webRequest);

  @PostMapping(
      value = "/{id}/$close",
      consumes = {"application/fhir+json", "application/json+fhir", APPLICATION_JSON_VALUE},
      produces = {"application/fhir+json", "application/json+fhir", APPLICATION_JSON_VALUE})
  @Operation(
      operationId = "closeBatch",
      summary = "Close an existing batch",
      description =
          "Closes the batch identified by the given id. Once closed, no further documents can be "
              + "uploaded. Clients should poll the statistics "
              + "resource referenced by the Content-Location header after waiting for the "
              + "duration indicated by the Retry-After header.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Batch close request accepted; processing started asynchronously.",
        headers = {
          @Header(
              name = HttpHeaders.RETRY_AFTER,
              description = "Suggested delay in seconds before polling the statistics resource.",
              schema = @Schema(type = "integer", example = "30")),
          @Header(
              name = HttpHeaders.CONTENT_LOCATION,
              description = "Location of the batch statistics resource.",
              schema =
                  @Schema(
                      type = "string",
                      format = "uri",
                      example =
                          "http://localhost:8080/batch/fhir/bundle/550e8400-e29b-41d4-a716-446655440000/$statistics"))
        },
        content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "A required header (e.g. x-sender) is missing.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "403",
        description = "The sender is not permitted to close this batch.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "406",
        description =
            "The requested Accept media type is not supported. Supported media types are "
                + "application/fhir+json, application/json+fhir and application/json.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "415",
        description =
            "The request Content-Type is not supported. Supported media types are "
                + "application/fhir+json, application/json+fhir and application/json.",
        content = @Content(mediaType = "application/fhir+json", schema = @Schema(type = "string")))
  })
  ResponseEntity<Object> closeBatch(
      @Parameter(
              description = "Unique identifier of the batch to close.",
              required = true,
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathVariable
          final UUID id,
      @Parameter(
              name = HTTP_HEADER_X_SENDER,
              in = ParameterIn.HEADER,
              description = "Identifier of the sender that owns the batch.",
              required = true)
          @RequestHeader(HTTP_HEADER_X_SENDER)
          final String sender);
}

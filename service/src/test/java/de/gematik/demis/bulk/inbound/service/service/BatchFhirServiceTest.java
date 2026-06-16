package de.gematik.demis.bulk.inbound.service.service;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import de.gematik.demis.service.base.error.ServiceException;
import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchFhirServiceTest {

  private final FhirContext fhirContext = FhirContext.forCached(FhirVersionEnum.R4);
  private final BatchFhirService underTest = new BatchFhirService(fhirContext);

  @Test
  void createStartResponse() {
    final UUID batchId = UUID.randomUUID();
    final String url = "https://demis.rki.de/surveillance/antibiotic-resistance/v1/batch/upload/42";

    final String expectedBundleString =
"""
{
  "resourceType": "Bundle",
  "id": "%s",
  "meta": {
    "lastUpdated": "2026-01-26T15:14:39.234+01:00"
  },
  "type": "batch",
  "link": [
    {
      "relation": "edit",
      "url": "%s"
    }
  ]
}
"""
            .formatted(batchId, url);

    final Bundle result = underTest.createStartResponse(batchId, url);
    assertThat(result).isNotNull();
    // check lastUpdated
    assertThat(result.getMeta().getLastUpdated()).isCloseTo(new Date(), 1000);
    // and after that manipulate it to match expectation
    result.getMeta().setLastUpdatedElement(new InstantType("2026-01-26T15:14:39.234+01:00"));
    assertThat(fhirContext.newJsonParser().encodeResourceToString(result))
        .isEqualToIgnoringWhitespace(expectedBundleString);
  }

  @Nested
  class ValidateStartRequest {
    private static void assertServiceException(
        final ServiceException ex, final String expectedMessage) {
      assertThat(ex)
          .extracting(
              ServiceException::getErrorCode,
              ServiceException::getResponseStatus,
              ServiceException::getMessage)
          .containsExactly("INVALID_REQUEST", BAD_REQUEST, expectedMessage);
    }

    @Test
    void okay() {
      final String validBundle =
          """
          {
            "resourceType" : "Bundle",
            "type" : "batch"
          }
        """;
      assertDoesNotThrow(() -> underTest.validateStartRequest(validBundle));
    }

    @Test
    void notParseable() {
      final String request =
          """
              {
                "resourceType" : "Bundle",
                "type" : "batch"
              """;
      assertThatThrownBy(() -> underTest.validateStartRequest(request))
          .isInstanceOfSatisfying(
              ServiceException.class, ex -> assertServiceException(ex, "error parsing request"));
    }

    @Test
    void unsupportedBatchType() {
      final String request =
          """
                  {
                    "resourceType" : "Bundle",
                    "type" : "transaction"
                  }
                  """;
      assertThatThrownBy(() -> underTest.validateStartRequest(request))
          .isInstanceOfSatisfying(
              ServiceException.class,
              ex -> assertServiceException(ex, "bundle type must be batch but is TRANSACTION"));
    }

    @Test
    void resourceIsNoBundle() {
      final String request =
          """
                  {
                    "resourceType" : "Patient"
                  }
                  """;
      assertThatThrownBy(() -> underTest.validateStartRequest(request))
          .isInstanceOfSatisfying(
              ServiceException.class,
              ex -> assertServiceException(ex, "request must be a bundle but is Patient"));
    }

    @Test
    void unknownPropertiesAreIgnored() {
      final String request =
          """
                  {
                    "resourceType" : "Bundle",
                    "type" : "batch",
                    "xxx" : "doesNotExistAndIsIgnored"
                  }
                  """;
      assertDoesNotThrow(() -> underTest.validateStartRequest(request));
    }

    @Test
    void knownButIrrelevantPropertiesAreIgnored() {
      final String request =
          """
                      {
                        "resourceType" : "Bundle",
                        "type" : "batch",
                        "count" : "10"
                      }
                      """;
      assertDoesNotThrow(() -> underTest.validateStartRequest(request));
    }
  }
}

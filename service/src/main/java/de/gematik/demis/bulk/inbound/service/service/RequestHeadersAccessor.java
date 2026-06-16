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

import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;

import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.service.base.error.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Provides access to HTTP request headers. */
@Component
@AllArgsConstructor
public class RequestHeadersAccessor {

  private final HttpServletRequest httpServletRequest;

  /**
   * Determines the value of the "x-fhir-package-version" header from the HTTP request.
   *
   * @return The "x-fhir-package-version" header from the HTTP request.
   */
  public String determineHeaderXFhirPackageVersion() {
    return determineHeaderValue(HEADER_FHIR_PACKAGE_VERSION);
  }

  private String determineHeaderValue(final String headerName) {
    String headerValue = httpServletRequest.getHeader(headerName);
    if (headerValue == null || headerValue.isEmpty()) {
      throw new ServiceException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
          "Missing or empty " + headerName + " header");
    }
    return headerValue;
  }

  /**
   * Determines the value of the "x-fhir-package" header from the HTTP request.
   *
   * @return The "x-fhir-package" header from the HTTP request.
   */
  public String determineHeaderXFhirPackage() {
    return determineHeaderValue(HEADER_FHIR_PACKAGE);
  }
}

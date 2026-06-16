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

import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.FHIR_PACKAGE_VERSION;
import static de.gematik.demis.bulk.inbound.service.TestdataGenerator.PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE;
import static de.gematik.demis.bulk.inbound.service.messaging.messages.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.demis.bulk.inbound.service.exception.ErrorCode;
import de.gematik.demis.service.base.error.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.http.HttpStatus;

class RequestHeadersAccessorTest {

  @Test
  void shouldDetermineHeaderXFhirApiVersion() {
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE_VERSION))
        .thenReturn(FHIR_PACKAGE_VERSION);

    RequestHeadersAccessor requestHeadersAccessor = new RequestHeadersAccessor(httpServletRequest);
    String actualApiVersion = requestHeadersAccessor.determineHeaderXFhirPackageVersion();

    assertThat(actualApiVersion).isNotNull().isEqualTo(FHIR_PACKAGE_VERSION);
  }

  @Test
  void shouldDetermineHeaderXFhirProfileVersion() {
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE)).thenReturn(PACKAGE);

    RequestHeadersAccessor requestHeadersAccessor = new RequestHeadersAccessor(httpServletRequest);
    String actualApiVersion = requestHeadersAccessor.determineHeaderXFhirPackage();

    assertThat(actualApiVersion).isNotNull().isEqualTo(PACKAGE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldThrowServiceExceptionOnMissingFhirApiVersionVersion(String fhirApiVersion) {
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE_VERSION)).thenReturn(fhirApiVersion);

    RequestHeadersAccessor requestHeadersAccessor = new RequestHeadersAccessor(httpServletRequest);

    Throwable thrown = catchThrowable(requestHeadersAccessor::determineHeaderXFhirPackageVersion);

    assertThat(thrown).isInstanceOf(ServiceException.class);
    assertThat(((ServiceException) thrown).getResponseStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(((ServiceException) thrown).getErrorCode())
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    assertThat(thrown.getMessage())
        .isEqualTo("Missing or empty " + HEADER_FHIR_PACKAGE_VERSION + " header");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldThrowServiceExceptionOnMissingFhirProfile(String fhirProfile) {
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(httpServletRequest.getHeader(HEADER_FHIR_PACKAGE)).thenReturn(fhirProfile);

    RequestHeadersAccessor requestHeadersAccessor = new RequestHeadersAccessor(httpServletRequest);

    Throwable thrown = catchThrowable(requestHeadersAccessor::determineHeaderXFhirPackage);

    assertThat(thrown).isInstanceOf(ServiceException.class);
    assertThat(((ServiceException) thrown).getResponseStatus())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(((ServiceException) thrown).getErrorCode())
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    assertThat(thrown.getMessage())
        .isEqualTo("Missing or empty " + HEADER_FHIR_PACKAGE + " header");
  }
}

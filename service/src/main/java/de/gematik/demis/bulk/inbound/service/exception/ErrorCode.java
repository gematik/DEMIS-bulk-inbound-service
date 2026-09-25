package de.gematik.demis.bulk.inbound.service.exception;

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

import de.gematik.demis.service.base.error.ServiceException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST),
  BATCH_NOT_FOUND(HttpStatus.FORBIDDEN),
  BATCH_NOT_PERMITTED(HttpStatus.FORBIDDEN),
  BATCH_ALREADY_CLOSED(HttpStatus.FORBIDDEN),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  DB_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
  RABBIT_MQ_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  INCONSISTENT_AMOUNT_OF_IDS_TO_NOTIFICATION(HttpStatus.BAD_REQUEST),
  MISSING_DOCUMENT_IDS(HttpStatus.BAD_REQUEST),
  DUPLICATE_DOCUMENT_IDS(HttpStatus.BAD_REQUEST),
  EMPTY_DOCUMENT_ID(HttpStatus.BAD_REQUEST),
  ;

  private final HttpStatus httpStatus;

  public String getCode() {
    return name();
  }

  public ServiceException exception(final String message) {
    return exception(message, null);
  }

  public ServiceException exception(final String message, final Throwable cause) {
    return new ServiceException(getHttpStatus(), getCode(), message, cause);
  }
}

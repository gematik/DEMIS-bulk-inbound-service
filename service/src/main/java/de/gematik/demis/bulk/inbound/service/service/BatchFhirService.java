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

import static de.gematik.demis.bulk.inbound.service.exception.ErrorCode.INVALID_REQUEST;

import ca.uhn.fhir.context.FhirContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BatchFhirService {
  private final FhirContext fhirContext;

  public void validateStartRequest(final String request) {
    final IBaseResource resource;
    try {
      resource = fhirContext.newJsonParser().parseResource(request);
    } catch (final Exception ex) {
      throw INVALID_REQUEST.exception("error parsing request", ex);
    }
    if (resource instanceof Bundle bundle) {
      if (bundle.getType() != Bundle.BundleType.BATCH) {
        throw INVALID_REQUEST.exception("bundle type must be batch but is " + bundle.getType());
      }
    } else {
      throw INVALID_REQUEST.exception("request must be a bundle but is " + resource.fhirType());
    }
  }

  public Bundle createStartResponse(final UUID batchId, final String url) {
    final Bundle bundle = new Bundle();
    bundle.setId(batchId.toString());
    bundle.getMeta().setLastUpdatedElement(InstantType.now());
    bundle.setType(Bundle.BundleType.BATCH);
    bundle.addLink().setRelation("edit").setUrl(url);
    return bundle;
  }
}

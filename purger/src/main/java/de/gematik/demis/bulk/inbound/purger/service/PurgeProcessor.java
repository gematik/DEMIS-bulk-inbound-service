package de.gematik.demis.bulk.inbound.purger.service;

/*-
 * #%L
 * bulk-inbound-purger
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

import de.gematik.demis.bulk.inbound.purger.config.PurgerConfigProps;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeProcessor {

  private final DeleteService deleteService;
  private final PurgerConfigProps configProps;

  public void purgeDatabase() {
    final Instant deleteBeforeDate = calculateDeleteBeforeDate();
    log.info("delete all batches older than {}", deleteBeforeDate);
    final int rowsDeleted = deleteService.deleteBatches(deleteBeforeDate);
    log.info("{} batches deleted", rowsDeleted);
  }

  private Instant calculateDeleteBeforeDate() {
    return Instant.now().minus(configProps.retentionDays(), ChronoUnit.DAYS);
  }
}

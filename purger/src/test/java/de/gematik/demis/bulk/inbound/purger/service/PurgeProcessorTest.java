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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.bulk.inbound.purger.config.PurgerConfigProps;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeProcessorTest {

  @Mock DeleteService deleteService;
  @Mock PurgerConfigProps configProps;

  @InjectMocks PurgeProcessor underTest;

  @Test
  void shouldCallDeleteServiceExactlyOnce() {
    when(configProps.retentionDays()).thenReturn(14);
    when(deleteService.deleteBatches(any())).thenReturn(5);

    underTest.purgeDatabase();

    verify(deleteService).deleteBatches(any(Instant.class));
    verifyNoMoreInteractions(deleteService);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 7, 14, 30, 90, 365})
  void shouldCalculateDeleteBeforeDateForDifferentRetentionDays(int retentionDays) {
    final Instant expectedDeleteBeforeDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

    when(configProps.retentionDays()).thenReturn(retentionDays);
    when(deleteService.deleteBatches(any())).thenReturn(0);

    underTest.purgeDatabase();

    final ArgumentCaptor<Instant> captor = ArgumentCaptor.captor();
    verify(deleteService).deleteBatches(captor.capture());

    assertThat(captor.getValue())
        .isCloseTo(expectedDeleteBeforeDate, within(1, ChronoUnit.SECONDS));
  }
}

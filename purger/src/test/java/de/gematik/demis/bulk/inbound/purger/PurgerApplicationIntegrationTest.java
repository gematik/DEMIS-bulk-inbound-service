package de.gematik.demis.bulk.inbound.purger;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.bulk.inbound.purger.service.PurgeProcessor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    })
@Slf4j
class PurgerApplicationIntegrationTest {
  @MockitoSpyBean private PurgeProcessor processor;

  @Test
  void contextLoads() {
    // Note the call order: SpringBootTest starts the whole application context before calling this
    // test method
    // That means the command line runner is also already executed and completed.
    verify(processor, times(1)).purgeDatabase();
    log.info("Purger Job was executed successfully");
  }

  @TestConfiguration
  static class MockEntityManager {
    @Bean
    EntityManager entityManager() {
      final EntityManager entityManager = mock(EntityManager.class);
      final Query query = mock(Query.class, Answers.RETURNS_SELF);
      when(entityManager.createNativeQuery(anyString())).thenReturn(query);
      return entityManager;
    }
  }
}

package de.gematik.demis.bulk.inbound.service.test;

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

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

public class TestContainer {
  public static final RabbitMQContainer RABBIT_MQ_CONTAINER =
      new RabbitMQContainer("rabbitmq:4.2.3-management-alpine")
          .withEnv("RABBITMQ_CONFIG_FILE_LINE_1", "collect_statistics_interval = 100")
          .withEnv("RABBITMQ_CONFIG_FILE_LINE_2", "management.rates_mode = none");

  public static final PostgreSQLContainer POSTGRES_CONTAINER =
      new PostgreSQLContainer("postgres:16-alpine");
}

package de.gematik.demis.bulk.inbound.service.config;

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

import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import de.gematik.demis.service.base.security.crypto.HashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
class CryptoConfig {

  @Bean
  public HashService hashService(@Value("${app.hash.pepper}") final byte[] pepper) {
    return new HashService(pepper);
  }

  @Bean
  public AESEncryptionService aesEncryptionService(
      @Value("${app.encryption.current-key}") final byte[] currentSecret,
      @Value("${app.encryption.previous-key}") final byte[] previousSecret) {
    log.info("message encryption is active");
    return new AESEncryptionService(currentSecret, previousSecret);
  }
}

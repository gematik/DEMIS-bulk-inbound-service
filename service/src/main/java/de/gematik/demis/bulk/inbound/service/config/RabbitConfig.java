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

import de.gematik.demis.bulk.inbound.service.exception.RetryableException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Common configuration for RabbitMQ. */
@Configuration
@Slf4j
public class RabbitConfig {

  public static final String EXCHANGE = "bulk.exchange";
  public static final String ROUTING_KEY_IN = "in";
  public static final String IN_QUEUE = "bulk.in";

  @Bean
  public DirectExchange bulkExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
    return rabbitTemplate -> {
      rabbitTemplate.addBeforePublishPostProcessors(
          message -> {
            // ist bereits der spring default, aber sicher ist sicher
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
          });
      rabbitTemplate.setConfirmCallback(
          (correlationData, ack, cause) ->
              log.info(
                  "publish confirmed -> correlationData: {}, ack: {}, cause: {}",
                  correlationData,
                  ack,
                  cause));
      rabbitTemplate.setReturnsCallback(
          returned ->
              log.error(
                  "Message returned: Exchange={}, RoutingKey={}, ReplyText={}",
                  returned.getExchange(),
                  returned.getRoutingKey(),
                  returned.getReplyText()));
    };
  }

  @Bean
  public MessageRecoverer messageRecoverer() {
    return (message, throwable) -> {
      if (throwable instanceof RetryableException
          || throwable.getCause() instanceof RetryableException) {
        throw new ImmediateRequeueAmqpException(throwable);
      } else {
        throw new AmqpRejectAndDontRequeueException(throwable);
      }
    };
  }

  @Bean
  public RabbitListenerRetrySettingsCustomizer rabbitListenerRetrySettingsCustomizer() {
    return retrySettings -> retrySettings.setExceptionIncludes(List.of(RetryableException.class));
  }
}

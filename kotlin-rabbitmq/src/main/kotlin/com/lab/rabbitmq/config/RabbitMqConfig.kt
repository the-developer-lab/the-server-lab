package com.lab.rabbitmq.config

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.interceptor.RetryOperationsInterceptor

@Configuration
class RabbitMqConfig(
    private val cachingConnectionFactory: CachingConnectionFactory
) {

    @Bean
    fun createUserRegistrationQueue(): Queue {
        return QueueBuilder.durable("q.user-registration")
            .withArgument("x-dead-letter-exchange", "x.registration-failure")
            .withArgument("x-dead-letter-routing-key", "fall-back")
            .build()
    }

    @Bean
    fun converter(): Jackson2JsonMessageConverter {
        return Jackson2JsonMessageConverter()
    }

    @Bean
    fun createPostRegistrationSchema(): Declarables {
        return Declarables(
            FanoutExchange("x.post-registration"),
            Queue("q.send-email"),
            Queue("q.send-sms"),
            Binding("q.send-email", Binding.DestinationType.QUEUE, "x.post-registration", "send-email", null),
            Binding("q.send-sms", Binding.DestinationType.QUEUE, "x.post-registration", "send-sms", null)
        )
    }


    @Bean
    fun retryInterceptor(): RetryOperationsInterceptor {
        return RetryInterceptorBuilder.stateless().maxAttempts(3)
            .backOffOptions(2000, 2.0, 100000)
            .recoverer(RejectAndDontRequeueRecoverer())
            .build()
    }

    @Bean
    fun registrationListenerContainerFactory(configurer: SimpleRabbitListenerContainerFactoryConfigurer): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        configurer.configure(factory, cachingConnectionFactory)
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO)
        factory.setAdviceChain(retryInterceptor())
        return factory
    }


    @Bean
    fun createDeadLetterSchema(): Declarables? {
        return Declarables(
            DirectExchange("x.registration-failure"),
            Queue("q.fall-back-registration"),
            Binding("q.fall-back-registration", Binding.DestinationType.QUEUE, "x.registration-failure", "fall-back", null)
        )
    }

    @Bean
    fun rabbitTemplate(converter: Jackson2JsonMessageConverter): RabbitTemplate {
        val rabbitTemplate = RabbitTemplate(cachingConnectionFactory)
        rabbitTemplate.messageConverter = converter
        return rabbitTemplate
    }
}

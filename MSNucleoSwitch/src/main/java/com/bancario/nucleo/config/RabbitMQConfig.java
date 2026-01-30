package com.bancario.nucleo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.transfers:ex.transfers.tx}")
    private String exchangeTransfers;

    @Value("${rabbitmq.exchange.dlx:ex.transfers.dlx}")
    private String exchangeDlx;

    @Bean
    public DirectExchange transferExchange() {
        return new DirectExchange(exchangeTransfers, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(exchangeDlx, true, false);
    }

    @Bean
    public Queue nexusQueue() {
        return QueueBuilder.durable("q.bank.NEXUS.in")
                .withArgument("x-dead-letter-exchange", exchangeDlx)
                .withArgument("x-dead-letter-routing-key", "NEXUS")
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    @Bean
    public Queue bantecQueue() {
        return QueueBuilder.durable("q.bank.BANTEC.in")
                .withArgument("x-dead-letter-exchange", exchangeDlx)
                .withArgument("x-dead-letter-routing-key", "BANTEC")
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    @Bean
    public Queue arcbankQueue() {
        return QueueBuilder.durable("q.bank.ARCBANK.in")
                .withArgument("x-dead-letter-exchange", exchangeDlx)
                .withArgument("x-dead-letter-routing-key", "ARCBANK")
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    @Bean
    public Queue ecusolQueue() {
        return QueueBuilder.durable("q.bank.ECUSOL.in")
                .withArgument("x-dead-letter-exchange", exchangeDlx)
                .withArgument("x-dead-letter-routing-key", "ECUSOL")
                .withArgument("x-message-ttl", 60000)
                .build();
    }


    @Bean
    public Queue nexusDlq() {
        return QueueBuilder.durable("q.bank.NEXUS.dlq").build();
    }

    @Bean
    public Queue bantecDlq() {
        return QueueBuilder.durable("q.bank.BANTEC.dlq").build();
    }

    @Bean
    public Queue arcbankDlq() {
        return QueueBuilder.durable("q.bank.ARCBANK.dlq").build();
    }

    @Bean
    public Queue ecusolDlq() {
        return QueueBuilder.durable("q.bank.ECUSOL.dlq").build();
    }

    @Bean
    public Binding nexusBinding() {
        return BindingBuilder.bind(nexusQueue()).to(transferExchange()).with("NEXUS");
    }

    @Bean
    public Binding bantecBinding() {
        return BindingBuilder.bind(bantecQueue()).to(transferExchange()).with("BANTEC");
    }

    @Bean
    public Binding arcbankBinding() {
        return BindingBuilder.bind(arcbankQueue()).to(transferExchange()).with("ARCBANK");
    }

    @Bean
    public Binding ecusolBinding() {
        return BindingBuilder.bind(ecusolQueue()).to(transferExchange()).with("ECUSOL");
    }

    @Bean
    public Binding nexusDlqBinding() {
        return BindingBuilder.bind(nexusDlq()).to(deadLetterExchange()).with("NEXUS");
    }

    @Bean
    public Binding bantecDlqBinding() {
        return BindingBuilder.bind(bantecDlq()).to(deadLetterExchange()).with("BANTEC");
    }

    @Bean
    public Binding arcbankDlqBinding() {
        return BindingBuilder.bind(arcbankDlq()).to(deadLetterExchange()).with("ARCBANK");
    }

    @Bean
    public Binding ecusolDlqBinding() {
        return BindingBuilder.bind(ecusolDlq()).to(deadLetterExchange()).with("ECUSOL");
    }


    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}

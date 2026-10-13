package com.ono.logginglibrary.autoconfigure.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.correlation.CorrelationPropagation
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory
import org.springframework.aop.framework.Advised
import org.springframework.beans.factory.config.BeanPostProcessor

/** Puts the current correlation id on a RabbitMQ message as it is published (unless the message already has one). */
class CorrelationMessagePostProcessor(private val header: String = CorrelationIds.HEADER) : MessagePostProcessor {
    override fun postProcessMessage(message: Message): Message {
        if (message.messageProperties.getHeader<Any?>(header) == null) {
            CorrelationIds.current()?.let { message.messageProperties.setHeader(header, it) }
        }
        return message
    }
}

/**
 * Wraps each message a `@RabbitListener` handles: the id from the message header becomes the thread's correlation id for the
 * handler (a fresh one when the sender sent none, so the consumer's logs still have an id), and the thread's previous value is
 * restored afterwards.
 */
class CorrelationRabbitListenerAdvice(
    private val header: String = CorrelationIds.HEADER,
    private val generator: CorrelationIdGenerator = CorrelationIdGenerator.DEFAULT
) : MethodInterceptor {
    override fun invoke(invocation: MethodInvocation): Any? {
        val message = invocation.arguments.firstOrNull { it is Message } as? Message
        val id = CorrelationIds.resolve(message?.messageProperties?.getHeader<Any?>(header)?.toString(), null, generator::generate)
        return CorrelationPropagation.withId(id) { invocation.proceed() }
    }
}

/** Adds [CorrelationRabbitListenerAdvice] to every `@RabbitListener` container factory in the context. */
class CorrelationRabbitFactoryPostProcessor(private val advice: CorrelationRabbitListenerAdvice) : BeanPostProcessor {
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        if (bean is AbstractRabbitListenerContainerFactory<*>) {
            val current = bean.adviceChain?.toList().orEmpty()
            if (current.none { it is CorrelationRabbitListenerAdvice }) {
                bean.setAdviceChain(*(current + advice).toTypedArray())
            }
        }
        return bean
    }
}

package com.zendesk.maxwell.producer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.row.RowMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitmqProducer extends AbstractProducer {

	private static final Logger LOGGER = LoggerFactory.getLogger(RabbitmqProducer.class);
	private static String exchangeName;
	private Channel channel;
	public RabbitmqProducer(MaxwellContext context) {
		super(context);
		exchangeName = context.getConfig().rabbitmqExchange;

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(context.getConfig().rabbitmqHost);
		factory.setUsername(context.getConfig().rabbitmqUser);
		factory.setPassword(context.getConfig().rabbitmqPass);
		factory.setVirtualHost(context.getConfig().rabbitmqVirtualHost);
		try {
			this.channel = factory.newConnection().createChannel();
			this.channel.exchangeDeclare(exchangeName, context.getConfig().rabbitmqExchangeType, context.getConfig().rabbitMqExchangeDurable, context.getConfig().rabbitMqExchangeAutoDelete, null);
		} catch (IOException | TimeoutException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void push(RowMap r) throws Exception {
		if ( !r.shouldOutput(outputConfig) ) {
			context.setPosition(r.getPosition());

			return;
		}

		String value = r.toJSON(outputConfig);
		String routingKey = getRoutingKeyFromTemplate(r);

		channel.basicPublish(exchangeName, routingKey, null, value.getBytes());
		if ( r.isTXCommit() ) {
			context.setPosition(r.getPosition());
		}
		if ( LOGGER.isDebugEnabled()) {
			LOGGER.debug("->  routing key:" + routingKey + ", partition:" + value);
		}
	}

	private String getRoutingKeyFromTemplate(RowMap r) {
		return context
				.getConfig()
				.rabbitmqRoutingKeyTemplate
				.replace("%db%", r.getDatabase())
				.replace("%table%", r.getTable());
	}
}

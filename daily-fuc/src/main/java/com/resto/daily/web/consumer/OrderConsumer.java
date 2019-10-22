package com.resto.daily.web.consumer;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import com.resto.brand.core.util.MQSetting;
@Component
public class OrderConsumer{

	Logger log = LoggerFactory.getLogger(getClass());
	Consumer consumer =  null;

	@Resource
	OrderMessageListener orderMessageListener;

	@PostConstruct
	public void startConsumer(){
		Properties pro= MQSetting.getPropertiesWithAccessSecret();
		pro.setProperty(PropertyKeyConst.ConsumerId, MQSetting.CID_ORDER);
		log.info("正在启动消费者");
		consumer = ONSFactory.createConsumer(pro);
		consumer.subscribe(MQSetting.TOPIC_RESTO_SHOP, MQSetting.TAG_PLACE_ORDER+ "||"+ MQSetting.TAG_PLACE_NOPAY_ORDER+ "||"+ MQSetting.TAG_PLACE_PLATFORM_ORDER+ "||"+MQSetting.TAG_CHECK_ORDER + "||"+MQSetting.TAG_NOT_PRINT_ORDER+"||"+MQSetting.TAG_NOTICE_ORDER+"||"+MQSetting.TAG_DELETE_ORDER+"||"+MQSetting.TAG_PRINT_SUCCESS+"||"+MQSetting.TAG_QUEUE_ORDER +
				"||"+MQSetting.TAG_SHOP_CHANGE + "||" + MQSetting.TAG_ORDER_CREATED + "||"+MQSetting.TAG_ORDER_PAY + "||"+MQSetting.TAG_ORDER_CANCEL + "||" +MQSetting.TAG_BAD_APPRAISE_PRINT_ORDER+"||"+MQSetting.TAG_RECEIPT_PRINT_SUCCESS+"||"+MQSetting.TAG_SERVER_COMMAND, orderMessageListener);
		consumer.start();
		log.info("消费者启动成功！:"+MQSetting.TOPIC_RESTO_SHOP+" CID:"+MQSetting.CID_ORDER);
	}

	@PreDestroy
	public void stopConsumer(){
		consumer.shutdown();
		log.info("消费者关闭！");
	}
}

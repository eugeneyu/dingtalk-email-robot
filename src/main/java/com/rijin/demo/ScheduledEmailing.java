package com.rijin.demo;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.PropertyKeyConst;

@Component
public class ScheduledEmailing {
	
	@Value("${accesskey.id}")
	private String accesskeyid;
	
	@Value("${accesskey.secret}")
	private String accesskeysecret;
	
	ApplicationContext ctx;
	Consumer consumer;
	
	public ScheduledEmailing () {
		ctx = null;
		
		
	}
	
	@Scheduled(cron = "0 15,16,17,18,36,37,38,39,45,46,47 * * * *")
    public void consumeMessageAndEmailing() {
		
		Properties properties = new Properties();
        // 您在 MQ 控制台创建的 Consumer ID
        properties.put(PropertyKeyConst.ConsumerId, "CID-dailyplan");
        // 鉴权用 AccessKey，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.AccessKey, accesskeyid);
        // 鉴权用 SecretKey，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.SecretKey, accesskeysecret);
        // 设置 TCP 接入域名，进入 MQ 控制台的消费者管理页面，在右侧操作列单击获取接入点获取
        // 此处以公有云公网地域接入点为例
        properties.put(PropertyKeyConst.ONSAddr,
          "http://onsaddr-internet.aliyun.com/rocketmq/nsaddr4client-internet");
        Consumer consumer = ONSFactory.createConsumer(properties);
        consumer.subscribe("dd-daily-plan", "*", new MessageListener() {
            public Action consume(Message message, ConsumeContext context) {
            	String messageBody = new String(message.getBody());
                System.out.println("Receive: " + messageBody);
                return Action.CommitMessage;
            }
        });
        
        
        //if (ctx == null) {
        //	ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        //}
    	
    	//ConsumerThread thread = (ConsumerThread) ctx.getBean("consumerThread");
    	//thread.setConsumer(consumer);;
    	//thread.start();
    	
        System.out.println("Consumer start");
        consumer.start();
        System.out.println("Consumer start#");
        
    	try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
    	System.out.println("Consumer shutdown");
        consumer.shutdown();
        System.out.println("Consumer shutdown#");
        //thread.interrupt();
         
        
        System.out.println("Consumer shutdown");
	}
}

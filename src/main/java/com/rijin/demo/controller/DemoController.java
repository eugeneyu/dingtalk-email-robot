package com.rijin.demo.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.Producer;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import com.aliyun.openservices.ons.api.SendResult;

import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.MessageListener;
import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.dm.model.v20151123.SingleSendMailResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.rijin.demo.AppConfig;
import com.rijin.demo.ConsumerThread;

@RestController
public class DemoController {
	
	@Value("${accesskey.id}")
	private String accesskeyid;
	
	@Value("${accesskey.secret}")
	private String accesskeysecret;
	
	
	@SuppressWarnings("unchecked")
	@RequestMapping("/")
	String home(@RequestBody Map<String, Object> payload) {
		
		String group = "";
		String name = "";
		String text = "";
		
		
		for (Map.Entry<String, Object> entry : payload.entrySet()) {
			if (entry.getKey().equalsIgnoreCase("senderNick")) {
				name = (String)entry.getValue();
				//System.out.println("value is:"+entry.getValue());
			} else if (entry.getKey().equalsIgnoreCase("text")) {
				for (Map.Entry<String, Object> entry2 : ((Map<String, Object>)entry.getValue()).entrySet()) {
					if (entry2.getKey().equalsIgnoreCase("content")) {
						text = (String)entry2.getValue();
						//System.out.println("value is:"+entry2.getValue());
					}
				}
			} else if (entry.getKey().equalsIgnoreCase("conversationTitle")){
				group = (String)entry.getValue();
				//System.out.println("value is:"+entry.getValue());
			}
		}
		
		Properties properties = new Properties();
        // AccessKey 阿里云身份验证，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.AccessKey, accesskeyid);
        // SecretKey 阿里云身份验证，在阿里云服务器管理控制台创建
        properties.put(PropertyKeyConst.SecretKey, accesskeysecret);
        //设置发送超时时间，单位毫秒
        properties.setProperty(PropertyKeyConst.SendMsgTimeoutMillis, "3000");
        // 设置 TCP 接入域名，进入 MQ 控制台的生产者管理页面，在左侧操作栏单击获取接入点获取
        // 此处以公共云生产环境为例
        properties.put(PropertyKeyConst.ONSAddr,
          "http://onsaddr-internet.aliyun.com/rocketmq/nsaddr4client-internet");
        Producer producer = ONSFactory.createProducer(properties);
        // 在发送消息前，必须调用 start 方法来启动 Producer，只需调用一次即可
        
        String message = "{\"GroupName\":\""+group+"\","
        		+ "\"Name\":\""+name+"\","
        		+ "\"Text\":\""+text+"\""
        		+ "}";
        
        producer.start();
        //循环发送消息
        for (int i = 0; i < 1; i++){
            Message msg = new Message( //
                // Message 所属的 Topic
                "dd-daily-plan",
                // Message Tag 可理解为 Gmail 中的标签，对消息进行再归类，方便 Consumer 指定过滤条件在 MQ 服务器过滤
                "DailyPlan",
                // Message Body 可以是任何二进制形式的数据， MQ 不做任何干预，
                // 需要 Producer 与 Consumer 协商好一致的序列化和反序列化方式
                message.getBytes());
            // 设置代表消息的业务关键属性，请尽可能全局唯一。
            // 以方便您在无法正常收到消息情况下，可通过阿里云服务器管理控制台查询消息并补发
            // 注意：不设置也不会影响消息正常收发
            //msg.setKey("ORDERID_" + i);
            try {
                SendResult sendResult = producer.send(msg);
                // 同步发送消息，只要不抛异常就是成功
                if (sendResult != null) {
                    System.out.println(new Date() + " Send mq message success. Topic is:" + msg.getTopic() + " msgId is: " + sendResult.getMessageId());
                }
            }
            catch (Exception e) {
                // 消息发送失败，需要进行重试处理，可重新发送这条消息或持久化这条数据进行补偿处理
                System.out.println(new Date() + " Send mq message failed. Topic is:" + msg.getTopic());
                e.printStackTrace();
            }
        }
        // 在应用退出前，销毁 Producer 对象
        // 注意：如果不销毁也没有问题
        producer.shutdown();
        
		return "Hello World!";
	}
	
	@RequestMapping("/mail")
	String sendMail(@RequestBody Map<String, Object> payload) {
		// 如果是除杭州region外的其它region（如新加坡、澳洲Region），需要将下面的"cn-hangzhou"替换为"ap-southeast-1"、或"ap-southeast-2"。
        IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", accesskeyid, accesskeysecret);
        // 如果是除杭州region外的其它region（如新加坡region）， 需要做如下处理
        //try {
        //DefaultProfile.addEndpoint("dm.ap-southeast-1.aliyuncs.com", "ap-southeast-1", "Dm",  "dm.ap-southeast-1.aliyuncs.com");
        //} catch (ClientException e) {
        //e.printStackTrace();
        //}
        IAcsClient client = new DefaultAcsClient(profile);
        SingleSendMailRequest request = new SingleSendMailRequest();
        try {
        	//request.setVersion("2017-06-22");// 如果是除杭州region外的其它region（如新加坡region）,必须指定为2017-06-22
            request.setAccountName("dailyplan@mail.firstary.top");
            request.setFromAlias("SA(北京)");
            request.setAddressType(1);
            //request.setTagName("控制台创建的标签");
            request.setReplyToAddress(true);
            request.setToAddress("youzhi.yyz@alibaba-inc.com");
            //可以给多个收件人发送邮件，收件人之间用逗号分开，批量发信建议使用BatchSendMailRequest方式
            //request.setToAddress("邮箱1,邮箱2");
            
            String content =  "";
            try {
            	File file = ResourceUtils.getFile("classpath:mail.tpl");
            	content = new String(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
            	System.out.println(e.getMessage());
            }
            
            content = "aa {zq-working} bb {zq-location} cc {zq-plan}";
            
            content = content.replace("{zq-working}", "Y");
            content = content.replace("{zq-location}", "北京");
            content = content.replace("{zq-plan}", "哈哈哈");
            
            Date date = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd");
            String strDate = dateFormat.format(date);
            
            
            
            request.setSubject("互联网公共服务 - SA(北京) - 晨会纪要 - " + strDate);
            request.setHtmlBody(content);
            SingleSendMailResponse httpResponse = client.getAcsResponse(request);
        } catch (ServerException e) {
            e.printStackTrace();
        }
        catch (ClientException e) {
            e.printStackTrace();
        }
		return "OK";
	}
	
	@RequestMapping("/consume")
	String consumeMessage(@RequestBody Map<String, Object> payload) {
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
        consumer.start();
        System.out.println("Consumer Started");
		return "OK";
	}
	
	@RequestMapping("/thread")
	String thread(@RequestBody Map<String, Object> payload) {
		@SuppressWarnings("resource")
		ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);
    	
    	ConsumerThread thread = (ConsumerThread) ctx.getBean("consumerThread");
    	thread.setName("Thread 1");
    	thread.start();
		return "OK";
	}
	
	@RequestMapping("/test")
	String test(@RequestBody Map<String, Object> payload) {
		
		String content = "北京，  \n" + 
        		"1、搜狗CDN切量推动\n" + 
        		"2. 唔哩CDN测试支持\n" + 
        		"3. 聚力转码测试支持\n" + 
        		"4. A站视频转封装跟进\n" + 
        		"5. 掌阅大数据和文转音需求跟进";
        
        System.out.println("content is:"+content);
        
        Pattern pattern = Pattern.compile("(.*?)[，。、\\s]");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find())
        {
            System.out.println("location is:"+matcher.group(1));
        }
        
        pattern = Pattern.compile("(1[、。\\s\\.]\\s*.*)");
        matcher = pattern.matcher(content);
        if (matcher.find()) {
        	
        	String line1 = matcher.group(1);
        	String lineOther = content.substring(content.indexOf(line1), content.length());
        	System.out.println("text is:\n"+line1+lineOther);
        }
        
		return "OK";
	}
}

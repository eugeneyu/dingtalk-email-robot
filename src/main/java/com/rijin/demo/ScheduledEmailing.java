package com.rijin.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Consumer;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import com.aliyun.openservices.ons.api.ONSFactory;
import com.aliyun.openservices.ons.api.PropertyKeyConst;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.dm.model.v20151123.SingleSendMailResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author rijin
 *
 */
@Component
public class ScheduledEmailing {
	
	@Value("${accesskey.id}")
	private String accesskeyid;
	
	@Value("${accesskey.secret}")
	private String accesskeysecret;
	
	@Autowired
	private ResourceLoader resourceLoader;
	
	Map <String, DailyPlan> mapDailyPlan;

	
	public ScheduledEmailing () {
		mapDailyPlan = new HashMap <String, DailyPlan> ();
	}
	
	@Scheduled(cron = "0 0 9 * * *")
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
                processMessage(messageBody);
                return Action.CommitMessage;
            }
        });
        
        consumer.start();
        
    	try {
			Thread.sleep(300000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	
        consumer.shutdown();
        sendMail();
	}
	
	public void processMessage(String message) {
		ObjectMapper mapper = new ObjectMapper();
	    JsonNode jsonObj;
		try {
			message = message.replace("\n", "\\n").replace("\r", "\\n");
			jsonObj = mapper.readTree(message);
			JsonNode jsonNodeName = jsonObj.get("Name");
			JsonNode jsonNodePlan = jsonObj.get("Text");
			
			DailyPlan plan = new DailyPlan();
			plan.setName(jsonNodeName.textValue());
			plan.setWorking("Y");
			
			String text = jsonNodePlan.textValue();
			
			if ((text.contains("休假")||text.contains("请假"))
					&& !text.contains("晨会请假")) {
				plan.setWorking("N");
				plan.setPlan(text);
				return;
			}
			
			Pattern pattern = Pattern.compile("(.*?)[，。、\\s]");
	        Matcher matcher = pattern.matcher(text);
	        if (matcher.find()) {
	            plan.setLocation(matcher.group(1));
	        }
	        
	        pattern = Pattern.compile("(1[、。\\s\\.]\\s*.*)");
	        matcher = pattern.matcher(text);
	        if (matcher.find()) {
	        	
	        	String line1 = matcher.group(1);
	        	String lineOther = text.substring(text.indexOf(line1)+line1.length(), text.length());
	        	String planText = line1+lineOther;
	        	planText = planText.replace("\n", "<br>");
	        	plan.setPlan(planText);
	        }
						
	        System.out.println("processMessage() - plan - "+plan.getName()+","+plan.getLocation()+","+plan.getPlan()+","+plan.getWorking());			
			mapDailyPlan.put(jsonNodeName.textValue(), plan);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendMail() {
		
		if (mapDailyPlan.isEmpty()) {
			return;
		}
		
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
            	Resource resource = resourceLoader.getResource("classpath:mail.tpl");
                InputStream fileStream = resource.getInputStream(); 
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line + "\n");   
                }

                content = out.toString();
            } catch (IOException e) {
            	System.out.println(e.getMessage());
            }
            
            
            for (String name: mapDailyPlan.keySet()) {
            	DailyPlan plan = mapDailyPlan.get(name);
            	 if (name.contains("日进")) { 
            		 System.out.println("sendMail() - plan - "+plan.getName()+","+plan.getLocation()+","+plan.getPlan()+","+plan.getWorking());
            		 System.out.println("sendMail() - content - before: "+content);
            		 content = content.replace("{yyz-working}", plan.getWorking());
                     content = content.replace("{yyz-location}", plan.getLocation());
                     content = content.replace("{yyz-plan}", plan.getPlan());
                     System.out.println("sendMail() - content - after: "+content);
            	 } else if (name.contains("云福")) {
            		 content = content.replace("{zq-working}", plan.getWorking());
                     content = content.replace("{zq-location}", plan.getLocation());
                     content = content.replace("{zq-plan}", plan.getPlan());
            	 } else if (name.contains("万磊")) {
            		 content = content.replace("{wl-working}", plan.getWorking());
                     content = content.replace("{wl-location}", plan.getLocation());
                     content = content.replace("{wl-plan}", plan.getPlan());
            	 } else if (name.contains("孙刚")) {
            		 content = content.replace("{sg-working}", plan.getWorking());
                     content = content.replace("{sg-location}", plan.getLocation());
                     content = content.replace("{sg-plan}", plan.getPlan());
            	 } else if (name.contains("木百")) {
            		 content = content.replace("{wby-working}", plan.getWorking());
                     content = content.replace("{wby-location}", plan.getLocation());
                     content = content.replace("{wby-plan}", plan.getPlan());
            	 } else if (name.contains("韩虎")) {
            		 content = content.replace("{hh-working}", plan.getWorking());
                     content = content.replace("{hh-location}", plan.getLocation());
                     content = content.replace("{hh-plan}", plan.getPlan());
            	 } else if (name.contains("耿纯")) {
            		 content = content.replace("{jzh-working}", plan.getWorking());
                     content = content.replace("{jzh-location}", plan.getLocation());
                     content = content.replace("{jzh-plan}", plan.getPlan());
            	 }
            }
            
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        	Date date = new Date();
        	
        	content = content.replace("{email-date}", formatter.format(date));
        	
        	//System.out.println("content is:"+content);
            
            request.setSubject("互联网公共服务 - SA(北京) - 晨会纪要 - " + formatter.format(date));
            request.setHtmlBody(content);
            SingleSendMailResponse httpResponse = client.getAcsResponse(request);
            
            mapDailyPlan.clear();
        } catch (ServerException e) {
            e.printStackTrace();
        }
        catch (ClientException e) {
            e.printStackTrace();
        }
	}
}

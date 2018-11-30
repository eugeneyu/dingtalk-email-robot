package com.rijin.demo;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.aliyun.openservices.ons.api.Consumer;

@Component
@Scope("prototype")
public class ConsumerThread extends Thread{
	
	Consumer consumer;
	
	public void setConsumer(Consumer con) {
		consumer = con;
	}

	@Override
	public void run() {
		
		System.out.println(getName() + " is running");
		
		try {
			Thread.sleep(2000);
			//consumer.start();
			
		} catch (InterruptedException e) {
			System.out.println(getName() + " is interrupted");   
		}
		
		System.out.println(getName() + " is ending");
	}
}

package com.pb.models.utils;

public class StatusLoggerExample {
	
	public static void main(String[] args) {
		System.setProperty("log4j.configuration", "log4j-status.xml");
		StatusLogger.logText("test", "This is a test.");
	}
}

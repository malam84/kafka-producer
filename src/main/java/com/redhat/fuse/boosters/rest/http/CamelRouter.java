package com.redhat.fuse.boosters.rest.http;

import javax.ws.rs.core.MediaType;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.redhat.fuse.boosters.rest.http.pojo.ApiResponse;
import com.redhat.fuse.boosters.rest.http.pojo.User;

/**
 * A simple Camel REST DSL route that implements the greetings service.
 * 
 */
@Component
public class CamelRouter extends RouteBuilder {


	public final static String HEADER_BUSINESSID = "businessId"; //Optional custom correlation id received/sent from/to external systems

	@Autowired
    private KafkaMsgProcessor printEvents;
	
	@Value("${kafka.component.uri}")
	private String restRouteOut;
	
    @Override
    public void configure() throws Exception {

        // @formatter:off
        restConfiguration()
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Greeting REST API")
                .apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .apiProperty("base.path", "camel/")
                .apiProperty("api.path", "/")
                .apiProperty("host", "")
                .apiContextRouteId("doc-api")
            .component("servlet")
            .bindingMode(RestBindingMode.json);
        
        onException(CamelExecutionException.class)
        .to("log:exception-logger");
        
        rest("/user").description("User API")
        .produces(MediaType.APPLICATION_JSON).consumes(MediaType.APPLICATION_JSON)
		.skipBindingOnErrorCode(false) 
		.post("/").type(User.class)
		.description("Send user")
		.param().name(HEADER_BUSINESSID).type(RestParamType.header).description("Business transaction id. Defaults to a random uuid").required(false).dataType("string").endParam()
		.responseMessage().code(200).responseModel(ApiResponse.class).endResponseMessage() //OK
		.route().routeId("post-user")
		.convertBodyTo(String.class)
		.log("User received: ${body}").id("received-user") //This step gets an id, so we can refer it in test
		.setHeader(KafkaConstants.KEY, constant("{{kafka.key}}"))
		.to(restRouteOut)
        .log("Message sent to kafka with headers ${in.headers}; body: ${body}").id("kafka-producer-logger")
        .bean(printEvents)
		.endRest();
    }
}
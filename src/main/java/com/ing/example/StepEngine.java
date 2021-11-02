package com.ing.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.api.process.model.ProcessDefinition;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.process.runtime.connector.Connector;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.Task;
import org.activiti.api.task.model.builders.GetTasksPayloadBuilder;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.model.payloads.GetTasksPayload;
import org.activiti.api.task.runtime.TaskRuntime;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.Execution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.util.*;

@SpringBootApplication
@EnableScheduling
public class StepEngine implements CommandLineRunner {
    private Logger logger = LoggerFactory.getLogger(StepEngine.class);

    private final ProcessRuntime processRuntime;

    private final TaskRuntime taskRuntime;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    private final SecurityUtil securityUtil;

    private final ObjectMapper objectMapper;

    public StepEngine(ProcessRuntime processRuntime,
                      TaskRuntime taskRuntime,
                      TaskService taskService,
                      RuntimeService runtimeService,
                      SecurityUtil securityUtil,
                      ObjectMapper objectMapper) {
        this.processRuntime = processRuntime;
        this.taskRuntime = taskRuntime;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.securityUtil = securityUtil;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(StepEngine.class, args);

    }

    @Override
    public void run(String... args) {
        securityUtil.logInAs("system");

        Page<ProcessDefinition> processDefinitionPage = processRuntime.processDefinitions(Pageable.of(0, 10));
        logger.info("> Available Process definitions: " + processDefinitionPage.getTotalItems());
        for (ProcessDefinition pd : processDefinitionPage.getContent()) {
            logger.info("\t > Process definition: " + pd);
        }

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yy HH:mm:ss");

        LinkedHashMap content = new LinkedHashMap();
        logger.info("> Starting to process: " + content + " at " + formatter.format(new Date()));
        ProcessInstance processInstance = processRuntime.start(ProcessPayloadBuilder
                .start()
                .withProcessDefinitionKey("IDVProcess")
                .withName("Processing instance")
                .withVariable("content", objectMapper.convertValue(content, JsonNode.class))
                .build());

        // suitable means selection
        selectSuitableMeans(processInstance.getId());

    }

    private void selectSuitableMeans(String processId) {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processId)
                .activityId("Activity_1yctgb8")
                .singleResult();

        logger.info("> Found execution {}", execution);
        runtimeService.trigger(execution.getId(), Collections.singletonMap("selectedMean", "ID"));
        taskRuntime.tasks(Pageable.of(0, 10), new GetTasksPayloadBuilder()
                .withProcessInstanceId(processId).build());

        // -> return the tasks assigned to the current user
    }


    @Bean
    public Connector getSuitableMeans() {
        return integrationContext -> {
            LinkedHashMap suitableMeansContent = (LinkedHashMap) integrationContext.getInBoundVariables().get("content");
            suitableMeansContent.put("means", Arrays.asList("IDV", "ID", "V"));
            integrationContext.addOutBoundVariable("content", suitableMeansContent);
            logger.info("> Retrieved suitable means: {}", suitableMeansContent);
            return integrationContext;
        };
    }

    @Bean
    public Connector selectMean() {
        return integrationContext -> {

            logger.info("> Selection done:");
            logger.info("\t> Activity name: {}", integrationContext.getClientName());
            logger.info("\t> Inbound variables: {}", integrationContext.getInBoundVariables());
            logger.info("\t> Retrieved suitable means: {}", integrationContext.getInBoundVariables().get("selectedMean"));
            return integrationContext;
        };
    }

}

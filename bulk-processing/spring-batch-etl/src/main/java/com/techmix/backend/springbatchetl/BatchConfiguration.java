package com.techmix.backend.springbatchetl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techmix.backend.springbatchetl.domain.Example;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.net.MalformedURLException;
import java.util.Date;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

    @Autowired
    JobBuilderFactory jobBuilderFactory;

    @Autowired
    StepBuilderFactory stepBuilderFactory;

    @Bean
    public Job movieJob(Step movieStep) {
        return jobBuilderFactory.get("movieJob")
                .incrementer(new RunIdIncrementer())
                .flow(movieStep)
                .end()
                .build();
    }

    @Bean
    public Step movieStep() throws MalformedURLException {
        return stepBuilderFactory
                .get("movieStep")
                .listener(movieStepListener())
                .<Example, Example>chunk(10)
                .reader(jsonItemReader())
                .processor(movieListItemProcessor())
                .writer(movieGenreWriter())
                .build();
    }

    @Bean
    public JsonItemReader<Example> jsonItemReader() {

        ObjectMapper objectMapper = new ObjectMapper();
        // configure the objectMapper as required
        JacksonJsonObjectReader<Example> jsonObjectReader = new JacksonJsonObjectReader<>(Example.class);
        jsonObjectReader.setMapper(objectMapper);

        return new JsonItemReaderBuilder<Example>()
                .jsonObjectReader(jsonObjectReader)
                .resource(new FileSystemResource("full_export_1MB.json"))
                .name("tradeJsonItemReader")
                .build();

//        FileSystemResource fileSystemResource = new FileSystemResource("full_export_1MB.json");
//
//        System.err.println("fileSystemResource= " + fileSystemResource);
//
//        return new JsonItemReaderBuilder<Example>()
//                .jsonObjectReader(new JacksonJsonObjectReader<>(Example.class))
//                .resource(fileSystemResource)
//                .name("movieJsonItemReader")
//                .build();
    }

    @Bean
    public ItemProcessor<Example, Example> movieListItemProcessor() {
        return movie -> new Example();
    }

    @Bean
    public FlatFileItemWriter<Example> movieGenreWriter() {
        return new FlatFileItemWriterBuilder<Example>()
                .name("movieGenreWriter")
                .resource(new FileSystemResource("out/movies.csv"))
                .headerCallback(writer -> writer.write("Movie Title,Movie Genres"))
                .delimited()
                .delimiter(",")
                .names(new String[]{"genre"})
                .build();
    }

    @Bean
    public StepExecutionListener movieStepListener() {
        return new StepExecutionListener() {

            @Override
            public void beforeStep(StepExecution stepExecution) {
                stepExecution.getExecutionContext().put("start", new Date().getTime());
                System.out.println("Step name:" + stepExecution.getStepName() + " Started");
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                long elapsed = new Date().getTime() - stepExecution.getExecutionContext().getLong("start");
                System.out.println("Step name:" + stepExecution.getStepName() + " Ended. Running time is " + elapsed + " milliseconds.");
                System.out.println("Read Count:" + stepExecution.getReadCount() +
                        " Write Count:" + stepExecution.getWriteCount());
                return ExitStatus.COMPLETED;
            }
        };
    }

}

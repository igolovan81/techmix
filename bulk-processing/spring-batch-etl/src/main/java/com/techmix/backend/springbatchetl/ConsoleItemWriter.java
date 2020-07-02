package com.techmix.backend.springbatchetl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

public class ConsoleItemWriter<T> implements ItemWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleItemWriter.class);

    @Override
    public void write(List<? extends T> items) throws Exception {
        LOG.trace("Console item writer starts");
        for (T item : items) {
            LOG.info(item.toString());
        }
        LOG.trace("Console item writer ends");
    }
}


package com.techmix.backend.springbatchetl;

import com.techmix.backend.springbatchetl.domain.Person;
import org.springframework.batch.item.ItemProcessor;

public class PersonItemProcessor implements ItemProcessor<Person, Person> {

    @Override
    public Person process(Person item) throws Exception {
        return item;
    }
}

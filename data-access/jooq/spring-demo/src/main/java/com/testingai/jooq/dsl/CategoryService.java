package com.testingai.jooq.dsl;

import static com.testingai.jooq.generated.Tables.CATEGORY;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final DSLContext ctx;

	public CategoryView create(String name) {
		Long id = ctx.insertInto(CATEGORY, CATEGORY.NAME).values(name).returning(CATEGORY.ID).fetchOne().getId();
		return new CategoryView(id, name);
	}

	public CategoryView findById(Long id) {
		return ctx.selectFrom(CATEGORY).where(CATEGORY.ID.eq(id)).fetchOptional()
				.map(r -> new CategoryView(r.getId(), r.getName()))
				.orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
	}
}

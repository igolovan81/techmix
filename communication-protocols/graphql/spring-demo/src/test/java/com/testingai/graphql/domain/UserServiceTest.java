package com.testingai.graphql.domain;

import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserServiceTest.Config.class)
class UserServiceTest {

	@TestConfiguration
	static class Config {
		@Bean
		UserService userService(UserRepository userRepository) {
			return new UserService(userRepository);
		}
	}

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserService userService;

	@Test
	void findByUsername_mapsEntityToRecord() {
		UserEntity entity = new UserEntity();
		entity.setUsername("jordan");
		entity.setEmail("jordan@example.com");
		entity.setDisplayName("Jordan");
		entity.setRole(Role.CUSTOMER);
		userRepository.save(entity);

		assertThat(userService.findByUsername("jordan")).isPresent().get().satisfies(user -> {
			assertThat(user.username()).isEqualTo("jordan");
			assertThat(user.displayName()).isEqualTo("Jordan");
			assertThat(user.role()).isEqualTo(Role.CUSTOMER);
		});
	}

	@Test
	void findByIds_returnsMapKeyedById() {
		UserEntity a = new UserEntity();
		a.setUsername("a");
		a.setEmail("a@example.com");
		a.setDisplayName("A");
		a.setRole(Role.CUSTOMER);
		UserEntity saved = userRepository.save(a);

		Map<Long, User> byId = userService.findByIds(List.of(saved.getId()));

		assertThat(byId).containsKey(saved.getId());
		assertThat(byId.get(saved.getId()).username()).isEqualTo("a");
	}
}

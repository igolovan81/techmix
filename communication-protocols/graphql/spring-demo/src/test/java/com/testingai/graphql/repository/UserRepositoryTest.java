package com.testingai.graphql.repository;

import com.testingai.graphql.domain.Role;
import com.testingai.graphql.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void findByUsername_returnsUser_whenExists() {
		UserEntity user = new UserEntity();
		user.setUsername("jordan");
		user.setEmail("jordan@example.com");
		user.setDisplayName("Jordan");
		user.setRole(Role.CUSTOMER);
		userRepository.save(user);

		assertThat(userRepository.findByUsername("jordan")).isPresent().get().extracting(UserEntity::getDisplayName)
				.isEqualTo("Jordan");
	}

	@Test
	void findByUsername_returnsEmpty_whenUnknown() {
		assertThat(userRepository.findByUsername("unknown")).isEmpty();
	}
}

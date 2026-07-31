package com.testingai.graphql.domain;

import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public Optional<User> findByUsername(String username) {
		return userRepository.findByUsername(username).map(UserService::toUser);
	}

	public Map<Long, User> findByIds(List<Long> ids) {
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(UserEntity::getId, UserService::toUser));
	}

	static User toUser(UserEntity entity) {
		return new User(entity.getId(), entity.getUsername(), entity.getDisplayName(), entity.getRole());
	}
}

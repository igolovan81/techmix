package com.testingai.batch.seed;

import java.util.List;

import com.testingai.batch.domain.BatchType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSeedServiceTest {

	@InjectMocks
	private OrderSeedService orderSeedService;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@SuppressWarnings("unchecked")
	@Test
	void seed_shouldInsertRequestedCountAndReturnIt() {
		when(jdbcTemplate.batchUpdate(anyString(), any(List.class))).thenReturn(new int[5]);

		int result = orderSeedService.seed(BatchType.CHUNK, 5);

		assertThat(result).isEqualTo(5);

		ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
		verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());
		assertThat(captor.getValue()).hasSize(5);
	}
}

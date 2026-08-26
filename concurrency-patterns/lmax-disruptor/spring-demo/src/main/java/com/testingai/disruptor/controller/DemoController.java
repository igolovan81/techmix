package com.testingai.disruptor.controller;

import com.testingai.disruptor.diamond.DiamondResult;
import com.testingai.disruptor.diamond.DiamondService;
import com.testingai.disruptor.errors.ErrorsResult;
import com.testingai.disruptor.errors.ErrorsService;
import com.testingai.disruptor.parallel.ParallelHandlersService;
import com.testingai.disruptor.parallel.ParallelResult;
import com.testingai.disruptor.producer.ProducerComparisonService;
import com.testingai.disruptor.producer.ProducerStat;
import com.testingai.disruptor.single.SingleHandlerResult;
import com.testingai.disruptor.single.SingleHandlerService;
import com.testingai.disruptor.waitstrategy.WaitStrategyComparisonService;
import com.testingai.disruptor.waitstrategy.WaitStrategyStat;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo/disruptor")
public class DemoController {

	static final int MAX_EVENT_COUNT = 100_000;

	private final SingleHandlerService singleHandlerService;
	private final ParallelHandlersService parallelHandlersService;
	private final DiamondService diamondService;
	private final ProducerComparisonService producerComparisonService;
	private final WaitStrategyComparisonService waitStrategyComparisonService;
	private final ErrorsService errorsService;

	public DemoController(SingleHandlerService singleHandlerService, ParallelHandlersService parallelHandlersService,
			DiamondService diamondService, ProducerComparisonService producerComparisonService,
			WaitStrategyComparisonService waitStrategyComparisonService, ErrorsService errorsService) {
		this.singleHandlerService = singleHandlerService;
		this.parallelHandlersService = parallelHandlersService;
		this.diamondService = diamondService;
		this.producerComparisonService = producerComparisonService;
		this.waitStrategyComparisonService = waitStrategyComparisonService;
		this.errorsService = errorsService;
	}

	@PostMapping("/single")
	public SingleHandlerResult single(@RequestParam(defaultValue = "1000") int eventCount) {
		return singleHandlerService.process(validate(eventCount));
	}

	@PostMapping("/parallel")
	public ParallelResult parallel(@RequestParam(defaultValue = "1000") int eventCount) {
		return parallelHandlersService.process(validate(eventCount));
	}

	@PostMapping("/diamond")
	public DiamondResult diamond(@RequestParam(defaultValue = "1000") int eventCount) {
		return diamondService.process(validate(eventCount));
	}

	@PostMapping("/producer")
	public List<ProducerStat> producer(@RequestParam(defaultValue = "1000") int eventCount,
			@RequestParam(defaultValue = "4") int threads) {
		return producerComparisonService.compare(validate(eventCount), threads);
	}

	@PostMapping("/waitstrategy")
	public List<WaitStrategyStat> waitStrategy(@RequestParam(defaultValue = "10000") int eventCount) {
		return waitStrategyComparisonService.compare(validate(eventCount));
	}

	@PostMapping("/errors")
	public ErrorsResult errors(@RequestParam(defaultValue = "1000") int eventCount) {
		return errorsService.process(validate(eventCount));
	}

	private int validate(int eventCount) {
		if (eventCount < 1 || eventCount > MAX_EVENT_COUNT) {
			throw new IllegalArgumentException(
					"eventCount must be between 1 and " + MAX_EVENT_COUNT + ", got " + eventCount);
		}
		return eventCount;
	}
}

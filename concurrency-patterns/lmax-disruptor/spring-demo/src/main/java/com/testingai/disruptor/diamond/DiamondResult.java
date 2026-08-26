package com.testingai.disruptor.diamond;

import com.testingai.disruptor.domain.Fill;

import java.util.List;

public record DiamondResult(List<Fill> fills, int restingOrders, long elapsedMillis) {
}

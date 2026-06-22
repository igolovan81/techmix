package com.testingai.axon.controller;

import java.math.BigDecimal;

public record AddOrderLineRequest(String productId, int quantity, BigDecimal price) {
}

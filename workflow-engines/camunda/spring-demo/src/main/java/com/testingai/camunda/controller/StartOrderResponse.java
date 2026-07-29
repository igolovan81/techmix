package com.testingai.camunda.controller;

public record StartOrderResponse(String orderId, long processInstanceKey) {
}

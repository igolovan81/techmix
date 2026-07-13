package com.testingai.handlebars.model;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, int stock) {
}

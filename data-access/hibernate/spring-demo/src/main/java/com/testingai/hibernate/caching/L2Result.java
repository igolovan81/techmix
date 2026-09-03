package com.testingai.hibernate.caching;

public record L2Result(
		long missesAfterFirstRead, long hitsAfterFirstRead, long missesAfterSecondRead, long hitsAfterSecondRead) {
}

package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.Side;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class OrderMatchingEngine {

	private final Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> bidBooks = new ConcurrentHashMap<>();
	private final Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> askBooks = new ConcurrentHashMap<>();

	public List<Fill> match(OrderEvent incoming) {
		String orderId = incoming.getOrderId();
		String symbol = incoming.getSymbol();
		Side side = incoming.getSide();
		int remaining = incoming.getQuantity();
		BigDecimal price = incoming.getPrice();

		TreeMap<BigDecimal, Deque<RestingOrder>> oppositeBook = bookFor(side == Side.BUY ? askBooks : bidBooks, symbol,
				side == Side.BUY);

		List<Fill> fills = new ArrayList<>();
		while (remaining > 0 && !oppositeBook.isEmpty()) {
			Map.Entry<BigDecimal, Deque<RestingOrder>> best = oppositeBook.firstEntry();
			BigDecimal bestPrice = best.getKey();
			boolean crosses = side == Side.BUY ? price.compareTo(bestPrice) >= 0 : price.compareTo(bestPrice) <= 0;
			if (!crosses) {
				break;
			}

			Deque<RestingOrder> queue = best.getValue();
			RestingOrder resting = queue.peekFirst();
			int filledQuantity = Math.min(remaining, resting.quantity());

			fills.add(side == Side.BUY
					? new Fill(symbol, orderId, resting.orderId(), filledQuantity, bestPrice)
					: new Fill(symbol, resting.orderId(), orderId, filledQuantity, bestPrice));

			remaining -= filledQuantity;
			resting.reduceQuantity(filledQuantity);
			if (resting.quantity() == 0) {
				queue.pollFirst();
				if (queue.isEmpty()) {
					oppositeBook.remove(bestPrice);
				}
			}
		}

		if (remaining > 0) {
			TreeMap<BigDecimal, Deque<RestingOrder>> sameBook = bookFor(side == Side.BUY ? bidBooks : askBooks, symbol,
					side != Side.BUY);
			sameBook.computeIfAbsent(price, p -> new LinkedList<>())
					.addLast(new RestingOrder(orderId, symbol, side, remaining, price));
		}

		return fills;
	}

	public int restingOrderCount() {
		return countAll(bidBooks) + countAll(askBooks);
	}

	private TreeMap<BigDecimal, Deque<RestingOrder>> bookFor(
			Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> books, String symbol, boolean lowestFirst) {
		Comparator<BigDecimal> priceOrder = lowestFirst ? Comparator.naturalOrder() : Comparator.reverseOrder();
		return books.computeIfAbsent(symbol, s -> new TreeMap<>(priceOrder));
	}

	private int countAll(Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> books) {
		int total = 0;
		for (TreeMap<BigDecimal, Deque<RestingOrder>> book : books.values()) {
			for (Deque<RestingOrder> queue : book.values()) {
				total += queue.size();
			}
		}
		return total;
	}
}

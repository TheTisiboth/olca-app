package org.openlca.app.results.comparison.utils;

import java.math.BigDecimal;
import java.math.MathContext;

public class MathUtils {
	public static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();
		BigDecimal bd = new BigDecimal(Double.toString(value));
		bd = bd.round(new MathContext(places));
		return bd.doubleValue();
	}
}

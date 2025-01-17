package org.openlca.app.results.comparison.display;

public enum TargetCalculationEnum {
	PRODUCT_SYSTEM("Product system"), PROJECT("Project");

	private String criteria;

	TargetCalculationEnum(String c) {
		criteria = c;
	}

	public static TargetCalculationEnum getTarget(String c) {
		for (TargetCalculationEnum comparisonCriteria : values()) {
			if (comparisonCriteria.criteria.equals(c)) {
				return comparisonCriteria;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return criteria;
	}

	public static String[] valuesToString() {
		var criterias = values();
		String[] crits = new String[criterias.length];
		for (int i = 0; i < criterias.length; i++) {
			crits[i] = criterias[i].toString();
		}
		return crits;
	}
}

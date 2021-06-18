package org.openlca.app.results.comparison.display;

public class Config {

	public final int NB_Product_Results = 20;
	public final boolean displayResultValue = false;
	public final boolean useFakeResults = false;
	public final boolean useGradientColor = true;
	public final boolean useBezierCurve = false;
	public final ColorCellCriteria colorCellCriteria = ColorCellCriteria.PROCESS;
	public final TargetCalculationEnum targetCalculationCriteria = TargetCalculationEnum.PRODUCT_SYSTEM;
}

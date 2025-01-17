package org.openlca.app.results.comparison.display;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.LocationDao;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.CategoryDescriptor;
import org.openlca.core.model.descriptors.ImpactDescriptor;
import org.openlca.core.model.descriptors.LocationDescriptor;
import org.openlca.core.model.descriptors.ProcessDescriptor;
import org.openlca.core.results.Contribution;

public class Cell {

	private RGB rgb;
	private Point startingLinksPoint;
	private Point endingLinkPoint;
	private boolean isDrawable;
	static Config config;
	private Result result;
	private double minAmount;
	static ColorCellCriteria criteria;
	private boolean isCutoff;
	private Contributions contributions;
	private boolean isDisplayed;
	private Rectangle rectCell;
	private String tooltip;
	private boolean isSelected;
	static IDatabase db;
	private int linkNumber;
	private LocationDescriptor location;
	private String processName;
	private CategoryDescriptor processCategory;
	private Cell prevCell;
	public int x, y, width, height;

	public Cell(Contribution<CategorizedDescriptor> contributionsList, double minAmount, Contributions c,
			Cell prevCell) {
		this.minAmount = minAmount;
		contributions = c;
		result = new Result(contributionsList);
		isDrawable = false;
		isCutoff = false;
		this.prevCell = prevCell;
		rgb = computeRGB();
		isDisplayed = false;
		isSelected = false;
		linkNumber = 0;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		rectCell = new Rectangle(x, y, width, height);
	}

	public void setData(Point startingLinksPoint, Point endingLinkPoint, Rectangle rectCell, boolean isCutoff) {
		this.startingLinksPoint = startingLinksPoint;
		this.endingLinkPoint = endingLinkPoint;
		this.rectCell = rectCell;
		this.isCutoff = isCutoff;
		isDisplayed = true;
	}

	public Point getStartingLinkPoint() {
		return startingLinksPoint;
	}

	public void setStartingLinkPoint(Point startingLinksPoint) {
		this.startingLinksPoint = startingLinksPoint;
	}

	public Point getEndingLinkPoint() {
		return endingLinkPoint;
	}

	public void setEndingLinkPoint(Point endingLinkPoint) {
		this.endingLinkPoint = endingLinkPoint;
	}

	public void addLinkNumber() {
		linkNumber++;
	}

	public int getLinkNumber() {
		return linkNumber;
	}

	public boolean isSelected() {
		return isSelected;
	}

	public void setSelected(boolean isSelected) {
		this.isSelected = isSelected;
	}

	private void setTooltip() {
		var contribution = result.getContribution();
		var locationId = ((ProcessDescriptor) contribution.item).location;
		location = new LocationDao(db).getDescriptor(locationId);
		processName = contribution.item.name + " - " + location.code;
		processCategory = new CategoryDao(db).getDescriptor(contribution.item.category);
		var category = contributions.getImpactCategory();
		tooltip = "Process name: " + processName + "\n" + "Amount: " + contribution.amount + " "
				+ StringUtils.defaultIfEmpty(category.referenceUnit, "") + "\n" + "Process category: "
				+ processCategory.name;
	}

	public double getContributionAmount() {
		return result.getContribution().amount;
	}

	public LocationDescriptor getLocation() {
		return location;
	}

	public String getProcessName() {
		return processName;
	}

	public CategoryDescriptor getProcessCategory() {
		return processCategory;
	}

	public CategorizedDescriptor getProcess() {
		return result.getContribution().item;
	}

	public ImpactDescriptor getImpactCategory() {
		return contributions.getImpactCategory();
	}

	/**
	 * Lazy load of tooltip
	 * 
	 * @return The Process tooltip
	 */
	public String getTooltip() {
		if (tooltip != null)
			return tooltip;
		setTooltip();
		return tooltip;
	}

	public Result getResult() {
		return result;
	}

	public double getTargetValue() {
		return result.getValue();
	}

	public double getNormalizedValue() {
		return result.getValue() + Math.abs(minAmount);
	}

	public double getAmount() {
		return result.getAmount();
	}

	public double getNormalizedAmount() {
		return result.getAmount() + Math.abs(minAmount);
	}

	public RGB computeRGB() {
		if ( result.getContribution().amount == 0.0) {
			isDrawable = false;
			return new RGB(192, 192, 192); // Grey color for unfocused values (0 or null)
		}
		isDrawable = true;
		if (prevCell != null) {
			var rgbToBeAvoided = prevCell.getRgb();
			rgb = getRGB(rgbToBeAvoided);
		} else
			rgb = getRGB(null);
		return rgb;
	}

	private RGB getRGB(RGB rgbToAVoid) {
		return ColorPaletteHelper.getColor(result.getProcessDescriptor(), rgbToAVoid);
	}

	public Contributions getContributions() {
		return contributions;
	}

	public void resetDefaultRGB() {
		rgb = computeRGB();
	}

	public RGB getRgb() {
		return rgb;
	}

	public void setRgb(RGB rgb) {
		this.rgb = rgb;
	}

	public boolean isLinkDrawable() {
		return isDrawable && !isCutoff && isDisplayed;
	}

	public boolean isCutoff() {
		return isCutoff;
	}

	public void setIsDisplayed(boolean isDisplayed) {
		this.isDisplayed = isDisplayed;
	}

	public boolean isDisplayed() {
		return isDisplayed;
	}

	public boolean contains(Point p) {
		if (rectCell == null)
			return false;
		return rectCell.contains(p);
	}

	public boolean hasSameProduct(Cell c) {
		if (c == null)
			return false;
		return result.getContribution().item.equals(c.getResult().getContribution().item);
	}

	public String toString() {
		return rgb + " / " + result + " / [ " + rectCell.x + "; " + (rectCell.x + rectCell.width) + " ]";
	}

}

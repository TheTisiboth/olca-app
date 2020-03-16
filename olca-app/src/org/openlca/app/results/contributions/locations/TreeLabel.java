package org.openlca.app.results.contributions.locations;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.openlca.app.M;
import org.openlca.app.components.ContributionImage;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.util.Labels;
import org.openlca.app.util.Numbers;
import org.openlca.core.model.CategorizedEntity;
import org.openlca.core.model.Location;
import org.openlca.core.model.RootEntity;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.results.Contribution;
import org.openlca.util.Strings;

class TreeLabel extends ColumnLabelProvider implements ITableLabelProvider {

	String unit = "";

	private ContributionImage image = new ContributionImage();

	@Override
	public void dispose() {
		image.dispose();
		super.dispose();
	}

	@Override
	public Image getColumnImage(Object obj, int col) {
		if (!(obj instanceof Contribution))
			return null;
		Contribution<?> c = (Contribution<?>) obj;
		if (col == 1)
			return image.getForTable(c.share);
		if (col != 0)
			return null;
		if (c.item instanceof BaseDescriptor)
			return Images.get((BaseDescriptor) c.item);
		if (c.item instanceof CategorizedEntity)
			return Images.get((CategorizedEntity) c.item);
		return null;
	}

	@Override
	public String getColumnText(Object obj, int col) {
		if (!(obj instanceof Contribution))
			return null;
		Contribution<?> c = (Contribution<?>) obj;
		switch (col) {
		case 0:
			return getLabel(c);
		case 1:
			return Numbers.format(c.amount);
		case 2:
			return unit;
		default:
			return null;
		}
	}

	private String getLabel(Contribution<?> c) {
		if (c == null || c.item == null)
			return M.None;
		if (c.item instanceof Location) {
			Location loc = (Location) c.item;
			String label = loc.name;
			if (loc.code != null
					&& !Strings.nullOrEqual(loc.code, label)) {
				label += " - " + loc.code;
			}
			return label;
		}
		if (c.item instanceof BaseDescriptor)
			return Labels.name((BaseDescriptor) c.item);
		if (c.item instanceof RootEntity)
			return Labels.name((RootEntity) c.item);
		return null;
	}
}
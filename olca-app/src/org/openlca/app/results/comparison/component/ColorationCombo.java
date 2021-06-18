package org.openlca.app.results.comparison.component;

import org.eclipse.swt.widgets.Composite;
import org.openlca.app.results.comparison.display.ColorCellCriteria;
import org.openlca.app.viewers.combo.AbstractComboViewer;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.ProcessType;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.CategoryDescriptor;
import org.openlca.core.model.descriptors.LocationDescriptor;
import org.openlca.core.model.descriptors.ProcessDescriptor;

public class ColorationCombo extends AbstractComboViewer<CategorizedDescriptor> {

	public ColorationCombo(Composite parent) {
		super(parent);
		var p = new ProcessDescriptor();
		p.name = ColorCellCriteria.PROCESS.getCriteria();
		p.processType = ProcessType.UNIT_PROCESS;
		var c = new CategoryDescriptor();
		c.name = ColorCellCriteria.CATEGORY.getCriteria();
		c.categoryType = ModelType.PROCESS;
		LocationDescriptor l = new LocationDescriptor();
		l.name = ColorCellCriteria.LOCATION.getCriteria();

		CategorizedDescriptor[] a = { p, c, l };
		setInput(a);
		selectFirst();
	}

	@Override
	public Class<CategorizedDescriptor> getType() {
		return CategorizedDescriptor.class;
	}

	@Override
	public void select(CategorizedDescriptor value) {
		super.select(value);
	}

}

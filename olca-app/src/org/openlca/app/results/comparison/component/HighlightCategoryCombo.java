package org.openlca.app.results.comparison.component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.nebula.widgets.tablecombo.TableCombo;
import org.eclipse.swt.widgets.Composite;
import org.openlca.app.results.comparison.display.Contributions;
import org.openlca.app.viewers.combo.AbstractComboViewer;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.descriptors.CategoryDescriptor;

public class HighlightCategoryCombo extends AbstractComboViewer<CategoryDescriptor> {

	private IDatabase db;
	private Set<Long> categoriesRefId;

	public HighlightCategoryCombo(Composite parent, IDatabase db, CategoryDescriptor... values) {
		super(parent);
		this.db = db;
		setNullText("No process category selected");
		setInput(values);
	}

	@Override
	public Class<CategoryDescriptor> getType() {
		return CategoryDescriptor.class;
	}

	@Override
	public void select(CategoryDescriptor value) {
		if (value == null) {
			if (isNullable())
				((TableCombo) getViewer().getControl()).select(0);
		} else
			super.select(value);
	}

	/**
	 * Update the category list, according to the new contributions list. We are
	 * just takeing the whole process contributions which are shown, and get a set of
	 * process category out of it.
	 * 
	 * @param contributionsList
	 */
	public void updateCategories(List<Contributions> contributionsList) {
		var tmpCategoriesRefId = contributionsList.stream()
				.flatMap(c -> c.getList().stream().filter(cell -> cell.isLinkDrawable() && cell.getProcess() != null)
						.map(cell -> cell.getProcess().category))
				.distinct().collect(Collectors.toSet());
		if (!tmpCategoriesRefId.equals(categoriesRefId)) {
			categoriesRefId = tmpCategoriesRefId;
			var categoriesDescriptors = new CategoryDao(db).getDescriptors(categoriesRefId);
			categoriesDescriptors.sort((c1, c2) -> c1.name.compareTo(c2.name));
			setInput(categoriesDescriptors.toArray(CategoryDescriptor[]::new));
			selectFirst();
		}
	}
}
